/// <reference lib="webworker" />
/// <reference types="vite-plugin-pwa/client" />
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
} from 'workbox-precaching'
import { CacheFirst } from 'workbox-strategies'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { ExpirationPlugin } from 'workbox-expiration'

declare const self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: Array<{ url: string; revision: string | null }>
}

// 新 SW を即座にアクティブ化 + 既存タブを controller 配下に取り込む。
// vite-plugin-pwa の registerType: 'autoUpdate' と組み合わさって、
// install 完了後すぐに新 SW が controller になり、register 側で page.reload() される。
self.addEventListener('install', () => {
  self.skipWaiting()
})
self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

precacheAndRoute(self.__WB_MANIFEST ?? [])
cleanupOutdatedCaches()

// SPAのナビゲーションは全て index.html にフォールバック（ただし /api/** は除外）
registerRoute(
  new NavigationRoute(createHandlerBoundToURL('index.html'), {
    denylist: [/^\/api\//],
  }),
)

// API レスポンスは SW でキャッシュしない。
// TODO は IndexedDB を真のソースとし、認証情報やユーザーデータを
// キャッシュ経由で別アカウントに漏らさないため。

// 静的アセット: Cache First
registerRoute(
  ({ request }: { request: Request }) =>
    request.destination === 'style' ||
    request.destination === 'script' ||
    request.destination === 'font' ||
    request.destination === 'image',
  new CacheFirst({
    cacheName: 'static-assets',
    plugins: [
      new ExpirationPlugin({
        maxEntries: 200,
        maxAgeSeconds: 60 * 60 * 24 * 30,
      }),
    ],
  }),
)
