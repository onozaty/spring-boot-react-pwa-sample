import { Link } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'

export function OfflineUnavailable() {
  return (
    <div className="max-w-2xl mx-auto p-8">
      <div className="rounded-md bg-yellow-50 border border-yellow-300 px-6 py-8 text-center space-y-4">
        <p className="text-lg font-semibold text-yellow-800">
          オフラインでは利用できません
        </p>
        <p className="text-sm text-yellow-700">
          この機能はネットワーク接続が必要です。オンラインに戻ってから再度お試しください。
        </p>
        <Button asChild variant="outline">
          <Link to="/">ホームに戻る</Link>
        </Button>
      </div>
    </div>
  )
}
