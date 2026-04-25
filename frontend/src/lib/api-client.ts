import createClient from 'openapi-fetch'
import createQueryClient from 'openapi-react-query'
import type { paths } from '../generated/api'

const client = createClient<paths>({
  baseUrl: window.location.origin,
  fetch: (...args) => globalThis.fetch(...args),
})

let isRefreshing = false
let refreshPromise: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  if (isRefreshing) {
    return refreshPromise!
  }
  isRefreshing = true
  refreshPromise = (async () => {
    try {
      const res = await globalThis.fetch('/api/auth/refresh', {
        method: 'POST',
        credentials: 'same-origin',
      })
      return res.ok
    } catch {
      return false
    } finally {
      isRefreshing = false
      refreshPromise = null
    }
  })()
  return refreshPromise
}

client.use({
  async onResponse({ response, request }) {
    if (response.status !== 401) return response

    const url = new URL(request.url)
    const path = url.pathname
    // リフレッシュ・me エンドポイント自体は再試行しない（無限ループ防止）
    if (path === '/api/auth/refresh' || path === '/api/auth/me') {
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return response
    }

    const refreshed = await tryRefresh()
    if (!refreshed) {
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return response
    }

    // リフレッシュ成功 → 元リクエストをリトライ
    return globalThis.fetch(request)
  },
})

export const $api = createQueryClient(client)
export { client }
