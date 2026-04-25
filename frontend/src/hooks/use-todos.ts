import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { client } from '@/lib/api-client'
import type { components } from '@/generated/api'
import {
  deleteTodo,
  dequeueSyncOp,
  enqueueSyncOp,
  getPendingSyncOps,
  getTodoByServerId,
  getTodos,
  removeSyncOpsByLocalId,
  upsertTodo,
} from '@/lib/todo-store'
import type { TodoRecord } from '@/lib/db'

type ServerTodo = components['schemas']['Todo']

export const todosQueryKey = ['todos'] as const

function serverToLocal(todo: ServerTodo): TodoRecord {
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

async function fetchAndSyncTodos(): Promise<TodoRecord[]> {
  // 同期待ちの操作がある間はサーバー状態で IDB を上書きしない
  // （オフライン削除中のTODOが SW キャッシュから復活するのを防ぐ）
  const pendingOps = await getPendingSyncOps()
  if (pendingOps.length > 0) {
    return getTodos()
  }

  try {
    const { data } = await client.GET('/api/todos')
    if (data) {
      const serverIds = new Set(data.map((t) => t.id))
      for (const todo of data) {
        await upsertTodo(serverToLocal(todo))
      }
      // サーバーに無くなった（他端末で削除された）TODO を IDB からも削除
      const local = await getTodos()
      for (const t of local) {
        if (t.serverId && !serverIds.has(t.serverId)) {
          await deleteTodo(t.localId)
        }
      }
    }
  } catch {
    // オフラインまたはエラー時はIndexedDBのデータを返す
  }
  return getTodos()
}

export function useTodos() {
  return useQuery({
    queryKey: todosQueryKey,
    queryFn: fetchAndSyncTodos,
    staleTime: 1000 * 30,
  })
}

export function useTodoMutations() {
  const queryClient = useQueryClient()

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: todosQueryKey })

  const createTodo = useMutation({
    mutationFn: async (text: string) => {
      try {
        const { data } = await client.POST('/api/todos', { body: { text } })
        if (data) {
          await upsertTodo(serverToLocal(data))
          return
        }
      } catch {
        // フォールバックへ
      }
      // オフライン or API失敗時: IndexedDBに書き込み + キューイング
      const localId = crypto.randomUUID()
      const record: TodoRecord = {
        localId,
        serverId: null,
        userId: 0,
        text,
        done: false,
        updatedAt: new Date().toISOString(),
        syncStatus: 'pending',
      }
      await upsertTodo(record)
      await enqueueSyncOp('create', localId, { text })
    },
    onSuccess: invalidate,
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

      if (todo.serverId) {
        try {
          const { data } = await client.PUT('/api/todos/{id}', {
            params: { path: { id: todo.serverId } },
            body: { text: todo.text, done },
          })
          if (data) {
            await upsertTodo(serverToLocal(data))
            return
          }
        } catch {
          // フォールバックへ
        }
        await enqueueSyncOp('update', localId, { text: todo.text, done })
      }
      // ローカルに楽観的更新
      await upsertTodo({
        ...todo,
        done,
        updatedAt: new Date().toISOString(),
        syncStatus: 'pending',
      })
    },
    onSuccess: invalidate,
  })

  const deleteTodoMutation = useMutation({
    mutationFn: async (localId: string) => {
      const todos = await getTodos()
      const todo = todos.find((t) => t.localId === localId)
      if (!todo) {
        return
      }

      if (todo.serverId) {
        // サーバーに存在する: DELETE APIを叩く（失敗時はキューに積む）
        try {
          await client.DELETE('/api/todos/{id}', {
            params: { path: { id: todo.serverId } },
          })
        } catch {
          await enqueueSyncOp('delete', localId, { serverId: todo.serverId })
        }
      } else {
        // サーバーに未同期のローカルTODO: create 操作を取り消す
        await removeSyncOpsByLocalId(localId)
      }
      await deleteTodo(localId)
    },
    onSuccess: invalidate,
  })

  return { createTodo, toggleTodo, deleteTodo: deleteTodoMutation }
}

export async function processSyncQueue(): Promise<void> {
  const ops = await getPendingSyncOps()
  for (const op of ops) {
    try {
      if (op.type === 'create') {
        const { data } = await client.POST('/api/todos', {
          body: { text: op.payload.text as string },
        })
        if (data) {
          await upsertTodo(serverToLocal(data))
          const localTodo = await getTodos().then((ts) =>
            ts.find((t) => t.localId === op.localId),
          )
          if (localTodo) {
            await deleteTodo(op.localId)
          }
        }
      } else if (op.type === 'update') {
        const todo = await getTodoByServerId(op.payload.serverId as number)
        const existing =
          todo ??
          (await getTodos().then((ts) =>
            ts.find((t) => t.localId === op.localId),
          ))
        if (existing?.serverId) {
          const { data } = await client.PUT('/api/todos/{id}', {
            params: { path: { id: existing.serverId } },
            body: {
              text: op.payload.text as string,
              done: op.payload.done as boolean,
            },
          })
          if (data) {
            await upsertTodo({ ...serverToLocal(data) })
          }
        }
      } else if (op.type === 'delete') {
        await client.DELETE('/api/todos/{id}', {
          params: { path: { id: op.payload.serverId as number } },
        })
      }
      await dequeueSyncOp(op.id)
    } catch {
      // 個別失敗はスキップして次へ
    }
  }
}
