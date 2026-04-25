import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { createRouter, RouterProvider } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { registerSW } from 'virtual:pwa-register'
import { toast } from 'sonner'
import { routeTree } from './routeTree.gen'
import { todosQueryKey } from './hooks/use-todos'
import { processSyncQueue } from './hooks/use-todos'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      gcTime: 1000 * 60 * 10,
      retry: 1,
      refetchOnWindowFocus: false,
      networkMode: 'always',
    },
    mutations: {
      networkMode: 'always',
    },
  },
})

// Service Worker 登録
registerSW({ immediate: true })

// SW からの同期完了通知を受け取る
const syncChannel = new BroadcastChannel('todo-sync')
syncChannel.addEventListener('message', (event) => {
  if (event.data?.type === 'TODOS_UPDATED') {
    queryClient.invalidateQueries({ queryKey: todosQueryKey })
  }
  if (event.data?.type === 'SYNC_COMPLETED') {
    toast.success('オフライン中の変更を同期しました')
  }
})

// オンライン復帰時の同期トリガー
window.addEventListener('online', async () => {
  // Background Sync API 対応ブラウザ
  if ('serviceWorker' in navigator && 'SyncManager' in window) {
    const reg = await navigator.serviceWorker.ready
    await (
      reg as ServiceWorkerRegistration & {
        sync: { register(tag: string): Promise<void> }
      }
    ).sync.register('todo-sync')
  } else {
    // フォールバック: 直接処理
    await processSyncQueue()
    queryClient.invalidateQueries({ queryKey: todosQueryKey })
  }
})

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
