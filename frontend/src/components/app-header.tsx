import { Link } from '@tanstack/react-router'
import { useAuth } from '@/hooks/use-auth'
import { useOnlineStatus } from '@/hooks/use-online-status'
import { Button } from '@/components/ui/button'

export function AppHeader() {
  const { user, logout } = useAuth()
  const isOnline = useOnlineStatus()

  return (
    <header className="border-b px-8 py-3 flex items-center justify-between">
      <Link to="/" className="font-semibold">
        サンプルアプリ
      </Link>
      {user && (
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground">{user.name}</span>
          {isOnline && (
            <Button variant="ghost" size="sm" asChild>
              <Link to="/account/password">パスワード変更</Link>
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => logout()}
            disabled={!isOnline}
          >
            ログアウト
          </Button>
        </div>
      )}
    </header>
  )
}
