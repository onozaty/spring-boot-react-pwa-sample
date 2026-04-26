import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { client } from '@/lib/api-client'
import type { components } from '@/generated/api'
import {
  createTodoOnServer,
  deleteTodoOnServer,
  HttpError,
  serverToLocal,
  updateTodoOnServer,
} from '@/lib/sync-queue'
import {
  deleteTodo,
  enqueueSyncOp,
  getPendingSyncOps,
  getTodos,
  removeSyncOpsByLocalId,
  todosQueryKey,
  upsertTodo,
  type TodoRecord,
} from '@/lib/todo-store'
import { useReachability } from './use-reachability'

export { todosQueryKey, HttpError }

// useTodos の queryFn。サーバーとの同期と IDB 読み出しを兼ねる。
// reachable=false ならネットワークアクセスをせず IDB をそのまま返す。
async function fetchAndSyncTodos(reachable: boolean): Promise<TodoRecord[]> {
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
  let data: components['schemas']['Todo'][] | undefined
  try {
    const res = await client.GET('/api/todos')
    data = res.data
  } catch {
    // 通信失敗時は IDB を返すだけ
  }

  if (data) {
    // サーバーの全 TODO を IDB に反映 (upsert)。
    // serverToLocal の localId が一意なので、既存レコードの上書きになる。
    const serverIds = new Set(data.map((t) => t.id))
    for (const todo of data) {
      await upsertTodo(serverToLocal(todo))
    }
    // 他端末で削除された TODO は、ローカルにだけ残っているので消す。
    // 未同期のローカル TODO (serverId === null) は対象外なので安全。
    const local = await getTodos()
    for (const t of local) {
      if (t.serverId && !serverIds.has(t.serverId)) {
        await deleteTodo(t.localId)
      }
    }
  }
  return getTodos()
}

export function useTodos() {
  const reachable = useReachability()
  return useQuery({
    queryKey: todosQueryKey,
    // reachable はクロージャ経由で参照。false→true 遷移時の再 fetch は
    // useReachability 側で invalidateQueries(todosQueryKey) を呼ぶことで起こす。
    queryFn: () => fetchAndSyncTodos(reachable),
  })
}

export function useTodoMutations() {
  const queryClient = useQueryClient()
  const reachable = useReachability()

  // ミューテーション後は IDB から直接読み取って即時に React Query キャッシュへ反映する。
  // invalidateQueries だとサーバー往復の re-fetch が走って UI 反映に 0.5 秒程度のラグが出るため。
  const refreshFromIdb = async () => {
    const fresh = await getTodos()
    queryClient.setQueryData<TodoRecord[]>(todosQueryKey, fresh)
  }

  const createTodo = useMutation({
    // reachable=true: API で作成 → IDB に反映。失敗 (ネットワーク or HTTP) は onError へ。
    // reachable=false: API は叩かず仮 localId でローカル保存 + 'create' をキューに積む。
    //   processSyncQueue が後で再送し、採番された TODO に置き換える。
    mutationFn: async (text: string) => {
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
        updatedAt: new Date().toISOString(),
        syncStatus: 'pending',
      })
      await enqueueSyncOp({ type: 'create', localId, payload: { text } })
    },
    onSuccess: refreshFromIdb,
  })

  const toggleTodo = useMutation({
    mutationFn: async ({
      localId,
      done,
    }: {
      localId: string
      done: boolean
    }) => {
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
      // 未同期の新規 TODO (serverId===null) の場合は 'create' キューが既にあるのでローカル更新だけ。
      if (todo.serverId) {
        await enqueueSyncOp({
          type: 'update',
          localId,
          payload: { serverId: todo.serverId, text: todo.text, done },
        })
      }
      await upsertTodo({
        ...todo,
        done,
        updatedAt: new Date().toISOString(),
        syncStatus: 'pending',
      })
    },
    onSuccess: refreshFromIdb,
  })

  const deleteTodoMutation = useMutation({
    mutationFn: async (localId: string) => {
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
    },
    onSuccess: refreshFromIdb,
  })

  return { createTodo, toggleTodo, deleteTodo: deleteTodoMutation }
}
