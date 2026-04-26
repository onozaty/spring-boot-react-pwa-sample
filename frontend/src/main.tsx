import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { createRouter, RouterProvider } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { registerSW } from 'virtual:pwa-register'
import { routeTree } from './routeTree.gen'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      gcTime: 1000 * 60 * 10,
      retry: 1,
      refetchOnWindowFocus: false,
      // networkMode: 'always' = オフラインでも query/mutation を実行させる。
      // TODO 機能は IndexedDB をソースに動くため、navigator.onLine が false でも
      // queryFn (fetchAndSyncTodos) や mutationFn を呼んでもらう必要がある。
      networkMode: 'always',
    },
    mutations: {
      networkMode: 'always',
    },
  },
})

// vite.config.ts で registerType: 'autoUpdate' にしているので、
// 新 SW が install されたら自動でアクティブ化 + ページリロードされる。
registerSW({ immediate: true })

// オンライン復帰時のキュー消化は useReachability フック (__root.tsx で起動) が担う。

const router = createRouter({ routeTree, context: { queryClient } })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </StrictMode>,
)
