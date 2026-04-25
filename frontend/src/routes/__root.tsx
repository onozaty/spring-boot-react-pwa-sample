import {
  createRootRouteWithContext,
  Outlet,
  redirect,
  useRouterState,
} from '@tanstack/react-router'
import { Toaster } from 'sonner'
import { AppHeader } from '@/components/app-header'
import { meQueryOptions } from '@/hooks/use-auth'
import type { QueryClient } from '@tanstack/react-query'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  beforeLoad: async ({ location, context }) => {
    if (location.pathname.startsWith('/login')) return
    // オフライン時は認証確認をスキップ（me API に到達できないため）
    if (!navigator.onLine) return
    const user = await context.queryClient.ensureQueryData(meQueryOptions)
    if (user === null) throw redirect({ to: '/login' })
  },
  component: RootLayout,
})

function RootLayout() {
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
