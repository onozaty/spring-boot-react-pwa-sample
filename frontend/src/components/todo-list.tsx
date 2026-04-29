import { useState } from 'react'
import { toast } from 'sonner'
import { useReachability } from '@/hooks/use-reachability'
import {
  useSyncFailureStatus,
  useSyncQueueActions,
  useSyncQueueStatus,
  useTodoMutations,
  useTodos,
} from '@/hooks/use-todos'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export function TodoList() {
  const [text, setText] = useState('')
  const isReachable = useReachability()
  const { data: todos, isPending, isError } = useTodos()
  const { data: syncQueueStatus } = useSyncQueueStatus()
  const { data: hasSyncFailure = false } = useSyncFailureStatus()
  const { createTodo, toggleTodo, deleteTodo } = useTodoMutations()
  const { retrySync, discardSync } = useSyncQueueActions()
  const shouldShowSyncFailure =
    isReachable && hasSyncFailure && (syncQueueStatus?.pendingCount ?? 0) > 0

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed) return
    createTodo.mutate(trimmed, {
      onSuccess: () => {
        setText('')
        if (!isReachable)
          toast.info('オフライン：オンライン復帰後に同期されます')
      },
      onError: () => toast.error('TODOの追加に失敗しました'),
    })
  }

  if (isPending)
    return <p className="mt-8 text-muted-foreground">読み込み中...</p>
  if (isError)
    return <p className="mt-8 text-destructive">TODOの取得に失敗しました</p>

  // 新しいものが上に来るようソート:
  // - 未同期 (serverId=null) は今作成中なので最も新しい → 先頭
  // - サーバー上の TODO は id 降順 (新しい id が上)
  const sorted = [...(todos ?? [])].sort((a, b) => {
    if (a.serverId === null && b.serverId === null) {
      return a.localId.localeCompare(b.localId)
    }
    if (a.serverId === null) return -1
    if (b.serverId === null) return 1
    return b.serverId - a.serverId
  })

  return (
    <div className="space-y-4">
      {!isReachable && (
        <div className="rounded-md bg-yellow-50 border border-yellow-300 px-4 py-2 text-sm text-yellow-800">
          オフラインモードです。変更はオンライン復帰後に同期されます。
        </div>
      )}
      {shouldShowSyncFailure && (
        <div className="flex flex-col gap-3 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-900 sm:flex-row sm:items-center sm:justify-between">
          <span>同期できない変更があります。</span>
          <div className="flex shrink-0 gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => retrySync.mutate()}
              disabled={retrySync.isPending || discardSync.isPending}
            >
              再試行
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  disabled={retrySync.isPending || discardSync.isPending}
                >
                  破棄
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent size="sm">
                <AlertDialogHeader>
                  <AlertDialogTitle>
                    未同期の変更を破棄しますか？
                  </AlertDialogTitle>
                  <AlertDialogDescription>
                    オフライン中に行った未同期の TODO
                    操作を取り消し、サーバーの状態で一覧を更新します。
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>キャンセル</AlertDialogCancel>
                  <AlertDialogAction
                    variant="destructive"
                    onClick={() => discardSync.mutate()}
                  >
                    破棄
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>
      )}

      <form onSubmit={handleCreate} className="flex gap-2">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="新しいTODOを入力..."
          maxLength={500}
          className="flex-1"
        />
        <Button type="submit" disabled={createTodo.isPending || !text.trim()}>
          追加
        </Button>
      </form>

      {sorted.length === 0 && (
        <p className="text-muted-foreground text-sm">TODOがありません</p>
      )}

      <ul className="space-y-2">
        {sorted.map((todo) => (
          <li
            key={todo.localId}
            className="flex items-center gap-3 rounded-md border px-4 py-2"
          >
            <input
              type="checkbox"
              checked={todo.done}
              onChange={() =>
                toggleTodo.mutate(
                  { localId: todo.localId, done: !todo.done },
                  { onError: () => toast.error('更新に失敗しました') },
                )
              }
              className="h-4 w-4 shrink-0 cursor-pointer"
            />
            <span
              className={`flex-1 text-sm ${todo.done ? 'line-through text-muted-foreground' : ''}`}
            >
              {todo.text}
            </span>
            {todo.syncStatus === 'pending' && (
              <span className="rounded-full bg-yellow-100 px-2 py-0.5 text-xs text-yellow-700 shrink-0">
                同期待ち
              </span>
            )}
            <Button
              variant="ghost"
              size="sm"
              className="shrink-0 text-destructive hover:text-destructive"
              onClick={() =>
                deleteTodo.mutate(todo.localId, {
                  onError: () => toast.error('削除に失敗しました'),
                })
              }
            >
              削除
            </Button>
          </li>
        ))}
      </ul>
    </div>
  )
}
