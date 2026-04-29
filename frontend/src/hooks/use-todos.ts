import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  getPendingSyncOps,
  getTodos,
  syncQueueQueryKey,
  todosQueryKey,
  type TodoRecord,
} from '@/lib/todo-store'
import {
  createTodoSynced,
  deleteTodoSynced,
  discardPendingSyncQueue,
  fetchAndSyncTodos,
  processSyncQueue,
  toggleTodoSynced,
} from '@/lib/todo-sync'
import { useReachability } from '@/hooks/use-reachability'
import { useSyncFailure } from '@/hooks/use-sync-failure'

export { todosQueryKey }

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
  const { setHasSyncFailure } = useSyncFailure()

  // ミューテーション後は IDB から直接読み取って即時に React Query キャッシュへ反映する。
  // invalidateQueries だとサーバー往復の re-fetch が走って UI 反映に 0.5 秒程度のラグが出るため。
  const refreshFromIdb = async () => {
    const fresh = await getTodos()
    queryClient.setQueryData<TodoRecord[]>(todosQueryKey, fresh)
    setHasSyncFailure(false)
    queryClient.invalidateQueries({ queryKey: syncQueueQueryKey })
  }

  const createTodo = useMutation({
    mutationFn: (text: string) => createTodoSynced(text, reachable),
    onSuccess: refreshFromIdb,
  })

  const toggleTodo = useMutation({
    mutationFn: ({ localId, done }: { localId: string; done: boolean }) =>
      toggleTodoSynced(localId, done, reachable),
    onSuccess: refreshFromIdb,
  })

  const deleteTodoMutation = useMutation({
    mutationFn: (localId: string) => deleteTodoSynced(localId, reachable),
    onSuccess: refreshFromIdb,
  })

  return { createTodo, toggleTodo, deleteTodo: deleteTodoMutation }
}

export function useSyncQueueStatus() {
  return useQuery({
    queryKey: syncQueueQueryKey,
    queryFn: async () => {
      const ops = await getPendingSyncOps()
      return { pendingCount: ops.length }
    },
  })
}

export function useSyncQueueActions() {
  const queryClient = useQueryClient()
  const { setHasSyncFailure } = useSyncFailure()

  const refreshQueries = () => {
    queryClient.invalidateQueries({ queryKey: syncQueueQueryKey })
    queryClient.invalidateQueries({ queryKey: todosQueryKey })
  }

  const retrySync = useMutation({
    mutationFn: processSyncQueue,
    onSuccess: (result) => {
      setHasSyncFailure(result.failed)
      refreshQueries()
      if (result.failed) {
        // ユーザーが明示的に押した「再試行」なので失敗をその場で通知する。
        // (自動同期の失敗はバナーで伝えるため通知しない)
        toast.error('オフライン中の変更の同期に失敗しました')
        return
      }
      if (result.processed > 0) {
        toast.success('オフライン中の変更を同期しました')
      }
    },
  })

  const discardSync = useMutation({
    mutationFn: discardPendingSyncQueue,
    onSuccess: (result) => {
      setHasSyncFailure(false)
      refreshQueries()
      if (result.resynced) {
        toast.success('未同期の変更を破棄しました')
      } else {
        toast.info(
          '未同期の変更を破棄しました。サーバーとの再同期は次回オンライン時に行われます',
        )
      }
    },
    onError: () => toast.error('未同期の変更の破棄に失敗しました'),
  })

  return { retrySync, discardSync }
}
