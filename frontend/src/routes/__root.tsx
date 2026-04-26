import {
  createRootRouteWithContext,
  Outlet,
  redirect,
  useRouterState,
} from '@tanstack/react-router'
import { Toaster } from 'sonner'
import { AppHeader } from '@/components/app-header'
import { meQueryOptions } from '@/hooks/use-auth'
import { useReachabilityEffects } from '@/hooks/use-reachability'
import type { QueryClient } from '@tanstack/react-query'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  beforeLoad: async ({ location, context }) => {
    if (location.pathname.startsWith('/login')) return
    // オフライン時は認証確認をスキップ（me API に到達できないため）
    if (!navigator.onLine) return
    // ensureQueryData: キャッシュにあれば即返し、なければ fetch して待つ。
    // 同じ queryKey を `useQuery(meQueryOptions)` でも使うため、ここで取った結果が
    // コンポーネント側で再フェッチされずに再利用される。
    const user = await context.queryClient.ensureQueryData(meQueryOptions)
    if (user === null) throw redirect({ to: '/login' })
  },
  component: RootLayout,
})

function RootLayout() {
  // アプリ全体で 1 度だけ呼ぶ。
  // - ヘルスチェックの起動 (useReachability 経由で内部購読)
  // - false→true 遷移時の processSyncQueue 呼び出し
  // ここ以外で呼ぶと processSyncQueue が多重起動して op が複数回送信される。
  useReachabilityEffects()

  const isLogin = useRouterState({
    select: (s) => s.location.pathname.startsWith('/login'),
  })

  return (
    <>
      {!isLogin && <AppHeader />}
      <Outlet />
      <Toaster richColors position="top-center" closeButton />
    </>
  )
}
