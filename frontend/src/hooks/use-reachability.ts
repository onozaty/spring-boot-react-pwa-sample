import { useEffect, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useBrowserOnline } from '@/hooks/use-browser-online'
import { processSyncQueue } from '@/lib/todo-sync'
import { todosQueryKey } from '@/lib/todo-store'

const HEALTH_CHECK_INTERVAL_MS = 30_000

const reachabilityQueryKey = ['reachability'] as const

// /api/health に到達できれば true。失敗・タイムアウトは false。
async function checkReachable(): Promise<boolean> {
  try {
    const res = await fetch('/api/health', {
      method: 'GET',
      cache: 'no-store',
    })
    return res.ok
  } catch {
    return false
  }
}

/**
 * サーバーへの到達可能性を表す状態を返す。複数コンポーネントから自由に呼んでよい。
 *
 * - `navigator.onLine === false` のときは即 false (ヘルスチェックも止まる)。
 * - `navigator.onLine === true` のときは /api/health を 30 秒間隔でチェック。
 * - 初期値は楽観的に true (ヘルスチェック完了前でも API を試させる)。
 *
 * このフックは状態の購読のみを行う。`false → true` 遷移時の同期トリガーは
 * `useReachabilityEffects` を `__root.tsx` で 1 回だけ呼ぶことで起動する。
 */
export function useReachability(): boolean {
  const browserOnline = useBrowserOnline()

  // useQuery を polling として使う:
  // - refetchInterval で 30 秒ごとに checkReachable を実行
  // - enabled=false (browserOnline=false) のときは polling 停止
  // - refetchIntervalInBackground=false で非アクティブタブでは polling しない
  // - retry=false で失敗を即 false に反映 (周期も乱さない)
  const { data: healthOk = true } = useQuery({
    queryKey: reachabilityQueryKey,
    queryFn: checkReachable,
    enabled: browserOnline,
    refetchInterval: HEALTH_CHECK_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: false,
  })

  return browserOnline && healthOk
}

/**
 * `useReachability` に紐づく副作用を起動する。
 * アプリ全体で **1 箇所のみ**(`__root.tsx`) で呼ぶこと。複数箇所で呼ぶと
 * `processSyncQueue` が多重起動して同じ op が複数回サーバーに送信される。
 *
 * 起動する副作用:
 * - `visibilitychange` でタブ表示時にヘルスチェックを即実行
 * - `navigator.onLine` の `false → true` 遷移時にヘルスチェックを即実行
 * - `false → true` 遷移時に `processSyncQueue` を呼びキューを消化
 */
export function useReachabilityEffects(): void {
  const queryClient = useQueryClient()
  const reachable = useReachability()
  const browserOnline = useBrowserOnline()

  // タブが表示状態に戻ったらヘルスチェックを即実行
  useEffect(() => {
    const handler = () => {
      if (!document.hidden) {
        queryClient.invalidateQueries({ queryKey: reachabilityQueryKey })
      }
    }
    document.addEventListener('visibilitychange', handler)
    return () => document.removeEventListener('visibilitychange', handler)
  }, [queryClient])

  // navigator.onLine が true になったらヘルスチェックを即実行する。
  // online イベントハンドラから直接 invalidate すると、ハンドラ実行時点では
  // useQuery の enabled がまだ false なので fetch がスキップされる。
  // 値の変化として扱うことで、enabled=true になった後のエフェクトで
  // invalidate されて確実に fetch される。
  // マウント時 (browserOnline=true) の invalidate は useQuery 初回 fetch と
  // dedupe されるため実害なし。
  useEffect(() => {
    if (browserOnline) {
      queryClient.invalidateQueries({ queryKey: reachabilityQueryKey })
    }
  }, [browserOnline, queryClient])

  // false → true の遷移を検知してキューを消化する
  const prevReachable = useRef(reachable)
  useEffect(() => {
    const wasUnreachable = !prevReachable.current
    prevReachable.current = reachable
    if (!wasUnreachable || !reachable) return

    void (async () => {
      const processed = await processSyncQueue()
      if (processed > 0) {
        queryClient.invalidateQueries({ queryKey: todosQueryKey })
        toast.success('オフライン中の変更を同期しました')
      }
    })()
  }, [reachable, queryClient])
}
