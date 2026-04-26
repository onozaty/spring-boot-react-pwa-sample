import { createFileRoute, Link } from '@tanstack/react-router'
import { useReachability } from '@/hooks/use-reachability'

export const Route = createFileRoute('/')({
  component: TopPage,
})

function TopPage() {
  const isReachable = useReachability()
  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-3xl font-bold mb-8">ホーム</h1>
      {!isReachable && (
        <div className="mb-4 rounded-md bg-yellow-50 border border-yellow-300 px-4 py-2 text-sm text-yellow-800">
          オフラインモードです。一部機能は利用できません。
        </div>
      )}
      <ul className="space-y-2">
        <li>
          <Link
            to="/todos"
            className="text-primary underline underline-offset-4 hover:opacity-80"
          >
            TODO
          </Link>
        </li>
        {isReachable && (
          <li>
            <Link
              to="/users"
              className="text-primary underline underline-offset-4 hover:opacity-80"
            >
              ユーザー管理
            </Link>
          </li>
        )}
      </ul>
    </div>
  )
}
