import { Link } from '@tanstack/react-router'
import { useAuth } from '@/hooks/use-auth'
import { useReachability } from '@/hooks/use-reachability'
import { Button } from '@/components/ui/button'

export function AppHeader() {
  const { user, logout } = useAuth()
  const isReachable = useReachability()

  return (
    <header className="border-b px-8 py-3 flex items-center justify-between">
      <Link to="/" className="font-semibold">
        サンプルアプリ
      </Link>
      {user && (
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground">{user.name}</span>
          {isReachable && (
            <Button variant="ghost" size="sm" asChild>
              <Link to="/account/password">パスワード変更</Link>
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => logout()}
            disabled={!isReachable}
          >
            ログアウト
          </Button>
        </div>
      )}
    </header>
  )
}
