import { queryOptions, useQuery } from '@tanstack/react-query'
import { client } from '@/lib/api-client'
import { resetDB } from '@/lib/todo-store'
import type { components } from '@/generated/api'

type User = components['schemas']['User']

// 401 は「エラー」ではなく「未ログイン」として扱いたいので、
// $api.useQuery ではなく client.GET を直接呼んで null に変換する。
// queryOptions で括ることで、__root.tsx の `ensureQueryData(meQueryOptions)` と
// コンポーネントの `useQuery(meQueryOptions)` で同じ queryKey/queryFn を共有できる。
export const meQueryOptions = queryOptions<User | null>({
  queryKey: ['auth', 'me'],
  queryFn: async () => {
    const { data, response } = await client.GET('/api/auth/me')
    if (response.status === 401) return null
    return data ?? null
  },
  retry: false,
})

// 別アカウントへのデータ混入を防ぐため、IndexedDB を破棄する。
export async function clearLocalUserData(): Promise<void> {
  await resetDB()
}

export function useAuth() {
  const { data: user, isLoading } = useQuery(meQueryOptions)

  const logout = async () => {
    await client.POST('/api/auth/logout')
    await clearLocalUserData()
    window.location.href = '/login'
  }

  return {
    user: user ?? null,
    isLoading,
    logout,
  }
}
