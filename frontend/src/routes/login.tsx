import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { clearLocalUserData, meQueryOptions } from '@/hooks/use-auth'
import { client } from '@/lib/api-client'

export const Route = createFileRoute('/login')({
  component: LoginPage,
})

function LoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isPending, setIsPending] = useState(false)

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (isPending) return
    setError(null)
    setIsPending(true)

    try {
      const { response, data } = await client.POST('/api/auth/login', {
        body: { email, password },
      })

      if (!response.ok) {
        setError('メールアドレスまたはパスワードが正しくありません。')
        return
      }

      // 前ユーザーのローカルデータが残っているケースに備えてクリアしてから遷移する。
      await clearLocalUserData()
      // ログイン応答をそのままキャッシュへ書き込む。これがないと遷移先の
      // useQuery(meQueryOptions) が /api/auth/me を即再フェッチしてしまう。
      queryClient.setQueryData(meQueryOptions.queryKey, data ?? null)
      navigate({ to: '/' })
    } catch {
      setError('ログインに失敗しました。')
    } finally {
      setIsPending(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="w-full max-w-sm border rounded-lg p-8 space-y-6">
        <h1 className="text-2xl font-bold text-center">ログイン</h1>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">メールアドレス</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">パスワード</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </div>
          {error && <p className="text-destructive text-sm">{error}</p>}
          <Button type="submit" className="w-full" disabled={isPending}>
            {isPending ? 'ログイン中...' : 'ログイン'}
          </Button>
        </form>
      </div>
    </div>
  )
}
