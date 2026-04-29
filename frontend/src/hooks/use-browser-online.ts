import { useSyncExternalStore } from 'react'

function subscribeOnline(callback: () => void): () => void {
  window.addEventListener('online', callback)
  window.addEventListener('offline', callback)
  return () => {
    window.removeEventListener('online', callback)
    window.removeEventListener('offline', callback)
  }
}

function getBrowserOnline(): boolean {
  return navigator.onLine
}

/**
 * `navigator.onLine` を購読してその値を返す。
 * online/offline イベントで再レンダリングがトリガされる。
 */
export function useBrowserOnline(): boolean {
  return useSyncExternalStore(subscribeOnline, getBrowserOnline, () => true)
}
