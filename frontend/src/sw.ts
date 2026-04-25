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

interface SyncEvent extends ExtendableEvent {
  readonly tag: string
}

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

// Background Sync: オンライン復帰時にキューを処理
self.addEventListener('sync', (event) => {
  const syncEvent = event as SyncEvent
  if (syncEvent.tag === 'todo-sync') {
    syncEvent.waitUntil(runSyncQueue())
  }
})

const syncChannel = new BroadcastChannel('todo-sync')

async function runSyncQueue(): Promise<void> {
  const {
    getPendingSyncOps,
    dequeueSyncOp,
    upsertTodo,
    deleteTodo,
    getTodoByServerId,
    getTodos,
  } = await import('./lib/todo-store')

  const ops = await getPendingSyncOps()
  if (ops.length === 0) return

  for (const op of ops) {
    try {
      if (op.type === 'create') {
        const res = await fetch('/api/todos', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ text: op.payload.text }),
        })
        if (res.ok) {
          const data = await res.json()
          await upsertTodo({
            localId: `server-${data.id}`,
            serverId: data.id,
            userId: data.userId,
            text: data.text,
            done: data.done,
            updatedAt: data.updatedAt,
            syncStatus: 'synced',
          })
          const todos = await getTodos()
          const localTodo = todos.find((t) => t.localId === op.localId)
          if (localTodo) await deleteTodo(op.localId)
        }
      } else if (op.type === 'update') {
        const todo = await getTodoByServerId(op.payload.serverId as number)
        const todos = await getTodos()
        const existing = todo ?? todos.find((t) => t.localId === op.localId)
        if (existing?.serverId) {
          const res = await fetch(`/api/todos/${existing.serverId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({
              text: op.payload.text,
              done: op.payload.done,
            }),
          })
          if (res.ok) {
            const data = await res.json()
            await upsertTodo({
              localId: `server-${data.id}`,
              serverId: data.id,
              userId: data.userId,
              text: data.text,
              done: data.done,
              updatedAt: data.updatedAt,
              syncStatus: 'synced',
            })
          }
        }
      } else if (op.type === 'delete') {
        await fetch(`/api/todos/${op.payload.serverId}`, {
          method: 'DELETE',
          credentials: 'same-origin',
        })
      }
      await dequeueSyncOp(op.id)
    } catch {
      // 個別失敗はスキップ
    }
  }

  syncChannel.postMessage({ type: 'TODOS_UPDATED' })
  syncChannel.postMessage({ type: 'SYNC_COMPLETED' })
}
