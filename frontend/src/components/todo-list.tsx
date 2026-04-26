import { useState } from 'react'
import { toast } from 'sonner'
import { useReachability } from '@/hooks/use-reachability'
import { useTodoMutations, useTodos } from '@/hooks/use-todos'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

export function TodoList() {
  const [text, setText] = useState('')
  const isReachable = useReachability()
  const { data: todos, isPending, isError } = useTodos()
  const { createTodo, toggleTodo, deleteTodo } = useTodoMutations()

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
