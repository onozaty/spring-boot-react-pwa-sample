/**
 * TODO のオフライン対応同期ロジック。
 *
 * 設計方針:
 * - 「真のソース」は IndexedDB。サーバー応答を IDB に書き写し、UI は常に IDB を見る。
 * - オンラインの mutation は API → IDB の順に反映する。reachable=false のときだけ
 *   IDB に楽観的に書き、sync-queue に積んで後で再送する。
 * - 同期トリガーは `useReachability` フック内で、reachable が false→true に遷移したときに
 *   `processSyncQueue` を呼ぶ形で実装される。
 *
 * TodoRecord の状態:
 * - syncStatus: 'synced'  → サーバーと一致。`localId` は `server-${serverId}` の形。
 * - syncStatus: 'pending' → ローカル変更あり。サーバー未反映。
 *   - serverId === null    : オフライン中に新規作成された TODO
 *   - serverId !== null    : サーバーに存在するが更新が未送信
 *
 * このファイルの構成:
 * - HTTP 薄ラッパー (createTodoOnServer 等) と変換 (serverToLocal) と例外型 (HttpError)
 * - queryFn (fetchAndSyncTodos): GET /api/todos と IDB の同期
 * - 単発オーケストレーション (*Synced): ユーザー操作1回ごとの online/offline 分岐
 * - キュー消化 (processSyncQueue): reachability 復帰時にバッチで再送
 *
 * レイヤー: use-todos (hook bind) → todo-sync (このファイル) → todo-store (IDB)
 */
import { toast } from 'sonner'
import { client } from '@/lib/api-client'
import type { components } from '@/generated/api'
import {
  clearSyncQueue,
  deleteTodo,
  dequeueSyncOp,
  enqueueSyncOp,
  getPendingSyncOps,
  getTodoByServerId,
  getTodos,
  removeSyncOpsByLocalId,
  updatePendingCreateDone,
  upsertTodo,
  type SyncQueueRecord,
  type TodoRecord,
} from '@/lib/todo-store'

type ServerTodo = components['schemas']['Todo']
export type SyncQueueResult = { processed: number; failed: boolean }

// HTTP エラー (4xx/5xx) を表す例外。fetch の throw (通信失敗) と区別するため。
class HttpError extends Error {
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

// 各 OnServer 関数:
// - 成功                              → サーバー応答を返す (delete のみ void)
// - HTTP エラー (4xx/5xx)             → HttpError を throw
// - ネットワーク失敗 (fetch 自体の throw) → そのまま throw (TypeError 等)
// 呼び出し側は `useMutation.onError` でこれらを拾う、もしくは processSyncQueue 側で分岐する。
export async function createTodoOnServer(
  text: string,
  done: boolean = false,
): Promise<ServerTodo> {
  const result = await client.POST('/api/todos', { body: { text, done } })
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

// useTodos の queryFn。サーバーとの同期と IDB 読み出しを兼ねる。
// reachable=false ならネットワークアクセスをせず IDB をそのまま返す。
export async function fetchAndSyncTodos(
  reachable: boolean,
): Promise<TodoRecord[]> {
  // オフライン中は API を叩かない (reachable=false なら確実に失敗するため)
  if (!reachable) return getTodos()

  // 未送信の変更が残っているうちはサーバー応答で IDB を上書きしない。
  // 例: オフラインで削除した TODO がキューに積まれた状態で、
  //     サーバーから「まだ存在する」レスポンスを受けると IDB に復活してしまう。
  //     → キュー消化 (processSyncQueue) が完了するまでサーバー反映を保留する。
  const pendingOps = await getPendingSyncOps()
  if (pendingOps.length > 0) {
    return getTodos()
  }

  // queryFn では通信失敗・HTTP エラーいずれも吸収して IDB の内容を返したい
  // (PWA はサーバー障害でも動くべき)。
  let data: ServerTodo[] | undefined
  try {
    const res = await client.GET('/api/todos')
    data = res.data
  } catch {
    // 通信失敗時は IDB を返すだけ
  }

  if (data) {
    await syncLocalTodosWithServer(data)
  }
  return getTodos()
}

async function syncLocalTodosWithServer(data: ServerTodo[]): Promise<void> {
  // サーバーの全 TODO を IDB に反映 (upsert)。
  // serverToLocal の localId が一意なので、既存レコードの上書きになる。
  const serverIds = new Set(data.map((t) => t.id))
  for (const todo of data) {
    await upsertTodo(serverToLocal(todo))
  }
  // 他端末で削除された TODO は、ローカルにだけ残っているので消す。
  //
  // 「IDB を全削除してサーバー応答で再構築」ではなく差分削除にしているのは、
  // 未同期のローカル TODO (serverId === null, sync-queue に create op を持つ) を
  // 巻き込まないため。serverId を持つものだけ消す方針なら、未同期 TODO は
  // 対象外なので安全。
  const local = await getTodos()
  for (const t of local) {
    if (t.serverId && !serverIds.has(t.serverId)) {
      await deleteTodo(t.localId)
    }
  }
}

// online/offline を吸収する単発オーケストレーション。
// reachable=true: API + IDB。reachable=false: IDB に楽観的書き込み + sync-queue へ enqueue。

export async function createTodoSynced(
  text: string,
  reachable: boolean,
): Promise<void> {
  if (reachable) {
    const created = await createTodoOnServer(text)
    await upsertTodo(serverToLocal(created))
    return
  }
  const localId = crypto.randomUUID()
  // userId は IDB ではユニーク制約に使わないので 0 のダミーで足りる。
  await upsertTodo({
    localId,
    serverId: null,
    userId: 0,
    text,
    done: false,
    // 仮値。再送成功時に serverToLocal 経由で DB の値に上書きされる。
    updatedAt: new Date().toISOString(),
    syncStatus: 'pending',
  })
  await enqueueSyncOp({
    type: 'create',
    localId,
    payload: { text, done: false },
  })
}

export async function toggleTodoSynced(
  localId: string,
  done: boolean,
  reachable: boolean,
): Promise<void> {
  const todos = await getTodos()
  const todo = todos.find((t) => t.localId === localId)
  if (!todo) return

  // サーバー上にある TODO で reachable なら API で更新して終わり。
  if (todo.serverId && reachable) {
    const updated = await updateTodoOnServer(todo.serverId, {
      text: todo.text,
      done,
    })
    await upsertTodo(serverToLocal(updated))
    return
  }

  // サーバー TODO だがオフライン: 'update' をキューに積む。
  // 未同期の新規 TODO (serverId===null) の場合は serverId が無いので update op を積めない。
  // 代わりに既にキューにある 'create' op の done を書き換えて、再送時にチェック状態ごと作成させる。
  if (todo.serverId) {
    await enqueueSyncOp({
      type: 'update',
      localId,
      payload: { serverId: todo.serverId, text: todo.text, done },
    })
  } else {
    await updatePendingCreateDone(localId, done)
  }
  await upsertTodo({
    ...todo,
    done,
    // 仮値。再送成功時に serverToLocal 経由で DB の値に上書きされる。
    updatedAt: new Date().toISOString(),
    syncStatus: 'pending',
  })
}

export async function deleteTodoSynced(
  localId: string,
  reachable: boolean,
): Promise<void> {
  const todos = await getTodos()
  const todo = todos.find((t) => t.localId === localId)
  if (!todo) return

  if (todo.serverId) {
    if (reachable) {
      // サーバー上の TODO: API で削除。失敗 (ネットワーク or HTTP) は onError へ。
      await deleteTodoOnServer(todo.serverId)
    } else {
      // オフライン: 'delete' をキューに積む。
      await enqueueSyncOp({
        type: 'delete',
        localId,
        payload: { serverId: todo.serverId },
      })
    }
  } else {
    // 未同期のローカル TODO を削除するケース:
    // サーバーには存在しないので削除 API は不要。むしろキューに積まれている
    // 'create' を打ち消さないと、後でゾンビ TODO がサーバーに作られてしまう。
    await removeSyncOpsByLocalId(localId)
  }
  await deleteTodo(localId)
}

// オフライン中に積まれた sync-queue を順番に消化する。
// HTTP エラー / ネットワーク失敗はいずれもユーザーの変更を失わないようキューに残す。
// 後続 op は前の op に依存する可能性があるため、最初の失敗で停止する。
export async function processSyncQueue(): Promise<SyncQueueResult> {
  const ops = await getPendingSyncOps()
  let processed = 0
  for (const op of ops) {
    try {
      await runQueuedOp(op)
      await dequeueSyncOp(op.id)
      processed++
    } catch (e) {
      if (e instanceof HttpError) {
        toast.error(`オフライン中の変更の同期に失敗しました (HTTP ${e.status})`)
      } else {
        toast.error('オフライン中の変更の同期に失敗しました')
      }
      return { processed, failed: true }
    }
  }
  return { processed, failed: false }
}

// ユーザーが「破棄」を選んだ場合、サーバー一覧の再取得が失敗してもローカルの
// キュー/仮 TODO は確実にクリアする。失敗を理由に破棄を不能にするとユーザーが
// 詰むため、サーバー反映は best-effort で行う (失敗時は次回 fetchAndSyncTodos で
// 整合する)。
// 戻り値の resynced はサーバー一覧と再同期できたかを示す。呼び出し元はこれを使って
// 「破棄完了」と「サーバー再同期は次回オンライン時」を出し分ける。
export async function discardPendingSyncQueue(): Promise<{
  resynced: boolean
}> {
  const ops = await getPendingSyncOps()
  for (const op of ops) {
    if (op.type === 'create') {
      await deleteTodo(op.localId)
    }
  }
  await clearSyncQueue()

  let data: ServerTodo[] | undefined
  try {
    const res = await client.GET('/api/todos')
    if (res.response.ok) {
      data = res.data
    }
  } catch {
    // 通信失敗時は IDB の現在状態のまま。次回 fetchAndSyncTodos で同期される。
  }
  if (!data) return { resynced: false }
  await syncLocalTodosWithServer(data)
  return { resynced: true }
}

// 1件の op を処理する。
// 成功時は何もせず、HTTP エラー / ネットワーク失敗は throw が伝播する。
// op は discriminated union なので、case 内で payload の型が自動的に絞り込まれる。
async function runQueuedOp(op: SyncQueueRecord): Promise<void> {
  switch (op.type) {
    case 'create': {
      const created = await createTodoOnServer(op.payload.text, op.payload.done)
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
