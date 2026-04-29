/* eslint-disable react-refresh/only-export-components */
// 「未同期キューの同期に失敗した」状態を UI 全体で共有するための小さな state。
// React Query で扱うほどのサーバー状態ではなく、純粋な UI フラグなので
// Context + useState で持つ。useQuery 化すると invalidate で意図せず false に
// 戻る事故を招くため避ける。
//
// このファイルは Context / Provider / hook を 1 つにまとめている。
// `react-refresh/only-export-components` (ファイル先頭で disable) はこれが理由。
// この context の更新頻度は低く Fast Refresh の恩恵が小さいため、
// ファイル分離より集約のメリットを優先する。
import { createContext, useContext, useMemo, useState } from 'react'

type SyncFailureContextValue = {
  hasSyncFailure: boolean
  setHasSyncFailure: (value: boolean) => void
}

const SyncFailureContext = createContext<SyncFailureContextValue | null>(null)

export function SyncFailureProvider({
  children,
  initialValue = false,
}: {
  children: React.ReactNode
  initialValue?: boolean
}) {
  const [hasSyncFailure, setHasSyncFailure] = useState(initialValue)
  const value = useMemo(
    () => ({ hasSyncFailure, setHasSyncFailure }),
    [hasSyncFailure],
  )
  return (
    <SyncFailureContext.Provider value={value}>
      {children}
    </SyncFailureContext.Provider>
  )
}

export function useSyncFailure(): SyncFailureContextValue {
  const ctx = useContext(SyncFailureContext)
  if (!ctx) {
    throw new Error('useSyncFailure must be used within SyncFailureProvider')
  }
  return ctx
}
