import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { OfflineUnavailable } from '@/components/offline-unavailable'
import { useReachability } from '@/hooks/use-reachability'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { client } from '@/lib/api-client'

export const Route = createFileRoute('/account/password')({
  component: PasswordChangePageGuard,
})

function PasswordChangePageGuard() {
  const isReachable = useReachability()
  if (!isReachable) return <OfflineUnavailable />
  return <PasswordChangePage />
}

function PasswordChangePage() {
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isPending, setIsPending] = useState(false)

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (isPending) return
    setError(null)
    setIsPending(true)

    try {
      const { response } = await client.PATCH('/api/auth/me/password', {
        body: { currentPassword, newPassword },
      })

      if (response.status === 400) {
        setError('現在のパスワードが正しくありません。')
        return
      }
      if (!response.ok) {
        setError('パスワードの変更に失敗しました。')
        return
      }

      toast.success('パスワードを変更しました。')
      navigate({ to: '/' })
    } catch {
      setError('パスワードの変更に失敗しました。')
    } finally {
      setIsPending(false)
    }
  }

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-3xl font-bold mb-8">パスワード変更</h1>
      <form onSubmit={handleSubmit} className="border rounded-lg p-6 space-y-4">
        <div className="space-y-2">
          <Label htmlFor="current-password">現在のパスワード</Label>
          <Input
            id="current-password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="new-password">新しいパスワード（8文字以上）</Label>
          <Input
            id="new-password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
        </div>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => navigate({ to: '/' })}
            disabled={isPending}
          >
            キャンセル
          </Button>
          <Button type="submit" disabled={isPending}>
            {isPending ? '変更中...' : '変更する'}
          </Button>
        </div>
      </form>
    </div>
  )
}
