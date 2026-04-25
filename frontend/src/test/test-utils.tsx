import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  createMemoryHistory,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { render, type RenderOptions } from '@testing-library/react'
import { meQueryOptions } from '@/hooks/use-auth'
import { routeTree } from '../routeTree.gen'
import { mockAuthUser } from './handlers'

interface Options extends Omit<RenderOptions, 'wrapper'> {
  initialEntries?: string[]
}

export function renderRoute(options: Options = {}) {
  const { initialEntries = ['/'], ...renderOptions } = options

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: Infinity,
        staleTime: Infinity,
        networkMode: 'always',
      },
      mutations: {
        retry: false,
        networkMode: 'always',
      },
    },
  })

  // beforeLoad の /api/auth/me fetch を回避するため認証済み状態を事前にセット
  queryClient.setQueryData(meQueryOptions.queryKey, mockAuthUser)

  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries }),
    defaultPendingMinMs: 0,
    context: { queryClient },
  })

  const result = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
    renderOptions,
  )

  return { ...result, router, queryClient }
}
