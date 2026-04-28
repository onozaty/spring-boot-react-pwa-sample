import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getTodos, todosQueryKey, type TodoRecord } from '@/lib/todo-store'
import {
  createTodoSynced,
  deleteTodoSynced,
  fetchAndSyncTodos,
  toggleTodoSynced,
} from '@/lib/todo-sync'
import { useReachability } from './use-reachability'

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

  // ミューテーション後は IDB から直接読み取って即時に React Query キャッシュへ反映する。
  // invalidateQueries だとサーバー往復の re-fetch が走って UI 反映に 0.5 秒程度のラグが出るため。
  const refreshFromIdb = async () => {
    const fresh = await getTodos()
    queryClient.setQueryData<TodoRecord[]>(todosQueryKey, fresh)
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
