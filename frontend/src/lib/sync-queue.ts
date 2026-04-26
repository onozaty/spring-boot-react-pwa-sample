/**
 * TODO のオフライン対応同期エンジン。
 *
 * 設計方針:
 * - 「真のソース」は IndexedDB。サーバー応答を IDB に書き写し、UI は常に IDB を見る。
 * - オンラインの mutation は API → IDB の順に反映する。reachable=false のときだけ
 *   IDB に楽観的に書き、`sync-queue` に積んで後で再送する。
 * - 同期トリガーは `useReachability` フック内で、reachable が false→true に遷移したときに
 *   `processSyncQueue` を呼ぶ形で実装される。
 *
 * TodoRecord の状態:
 * - syncStatus: 'synced'  → サーバーと一致。`localId` は `server-${serverId}` の形。
 * - syncStatus: 'pending' → ローカル変更あり。サーバー未反映。
 *   - serverId === null    : オフライン中に新規作成された TODO
 *   - serverId !== null    : サーバーに存在するが更新が未送信
 */
import { toast } from 'sonner'
import { client } from '@/lib/api-client'
import type { components } from '@/generated/api'
import {
  deleteTodo,
  dequeueSyncOp,
  getPendingSyncOps,
  getTodoByServerId,
  getTodos,
  upsertTodo,
  type SyncQueueRecord,
  type TodoRecord,
} from '@/lib/todo-store'

type ServerTodo = components['schemas']['Todo']

// サーバー応答を IDB レコード形式に変換する。
// localId は `server-${id}` で固定することで、同じサーバー TODO は常に同じ IDB レコードを
// 上書き (upsert) するようになり、重複が発生しない。
export function serverToLocal(todo: ServerTodo): TodoRecord {
  return {
    localId: `server-${todo.id}`,
    serverId: todo.id,
    userId: todo.userId,
    text: todo.text,
    done: todo.done,
    updatedAt: todo.updatedAt,
    syncStatus: 'synced',
  }
}

// HTTP エラー (4xx/5xx) を表す例外。fetch の throw (通信失敗) と区別するため。
export class HttpError extends Error {
  status: number
  constructor(status: number) {
    super(`API request failed: ${status}`)
    this.status = status
  }
}

// HTTP エラーレスポンスを HttpError に変換する。
// ネットワーク失敗 (fetch 自体の throw) はそのまま素通しする。
function ensureOk<T>(result: { data?: T; response: Response }): T {
  if (!result.response.ok) {
    throw new HttpError(result.response.status)
  }
  return result.data as T
}

// 各 OnServer 関数:
// - 成功                              → サーバー応答を返す (delete のみ void)
// - HTTP エラー (4xx/5xx)             → HttpError を throw
// - ネットワーク失敗 (fetch 自体の throw) → そのまま throw (TypeError 等)
// 呼び出し側は `useMutation.onError` でこれらを拾う、もしくは processSyncQueue 側で分岐する。
export async function createTodoOnServer(text: string): Promise<ServerTodo> {
  const result = await client.POST('/api/todos', { body: { text } })
  return ensureOk(result)
}

export async function updateTodoOnServer(
  serverId: number,
  body: { text: string; done: boolean },
): Promise<ServerTodo> {
  const result = await client.PUT('/api/todos/{id}', {
    params: { path: { id: serverId } },
    body,
  })
  return ensureOk(result)
}

export async function deleteTodoOnServer(serverId: number): Promise<void> {
  const result = await client.DELETE('/api/todos/{id}', {
    params: { path: { id: serverId } },
  })
  ensureOk(result)
}

// オフライン中に積まれた sync-queue を順番に消化する。
// 処理結果は3種類:
//   - 成功            → dequeue
//   - ネットワーク失敗 → op をキューに残し、次回オンライン復帰時に再試行
//   - HTTP エラー      → 永続失敗として toast 通知 + dequeue (再送しても成功しないので破棄)
// 戻り値: 成功して dequeue した op 数 (toast 抑制のため)。
export async function processSyncQueue(): Promise<number> {
  const ops = await getPendingSyncOps()
  let processed = 0
  for (const op of ops) {
    try {
      await runQueuedOp(op)
      await dequeueSyncOp(op.id)
      processed++
    } catch (e) {
      if (e instanceof HttpError) {
        // 永続失敗: 残しても無意味なので破棄。ユーザーには通知する。
        toast.error(`オフライン中の変更の同期に失敗しました (HTTP ${e.status})`)
        await dequeueSyncOp(op.id)
      }
      // ネットワーク失敗: op をキューに残して次回オンライン復帰時に再試行
    }
  }
  return processed
}

// 1件の op を処理する。
// 成功時は何もせず、HTTP エラー / ネットワーク失敗は throw が伝播する。
// op は discriminated union なので、case 内で payload の型が自動的に絞り込まれる。
async function runQueuedOp(op: SyncQueueRecord): Promise<void> {
  switch (op.type) {
    case 'create': {
      const created = await createTodoOnServer(op.payload.text)
      await upsertTodo(serverToLocal(created))
      // 仮 localId のレコードが残っていれば削除 (サーバー側で別 ID が採番されているため)。
      const localTodo = (await getTodos()).find((t) => t.localId === op.localId)
      if (localTodo) await deleteTodo(op.localId)
      return
    }
    case 'update': {
      // 対象 TODO を探す。通常は serverId 引きで見つかるが、
      // IDB の状態によっては localId フォールバックが必要 (防御的)。
      const byServerId = await getTodoByServerId(op.payload.serverId)
      const existing =
        byServerId ?? (await getTodos()).find((t) => t.localId === op.localId)
      // 対象 TODO がローカルで既に消えている場合 (例: 後続の delete 操作で消された)
      // → 更新する対象がないので op だけ消化して終わり。
      if (!existing?.serverId) return
      const updated = await updateTodoOnServer(existing.serverId, {
        text: op.payload.text,
        done: op.payload.done,
      })
      await upsertTodo(serverToLocal(updated))
      return
    }
    case 'delete': {
      await deleteTodoOnServer(op.payload.serverId)
      return
    }
  }
}
