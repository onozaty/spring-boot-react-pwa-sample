import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { $api } from '@/lib/api-client'
import { OfflineUnavailable } from '@/components/offline-unavailable'
import { useReachability } from '@/hooks/use-reachability'
import { UserForm } from '@/components/user-form'

export const Route = createFileRoute('/users/$id/edit')({
  component: EditUserPageGuard,
})

function EditUserPageGuard() {
  const isReachable = useReachability()
  if (!isReachable) return <OfflineUnavailable />
  return <EditUserPage />
}

function EditUserPage() {
  const { id } = Route.useParams()
  const navigate = useNavigate()

  const userId = Number(id)

  const {
    data: user,
    isPending,
    isError,
  } = $api.useQuery('get', '/api/users/{id}', {
    params: { path: { id: userId } },
  })

  if (isPending)
    return (
      <div className="max-w-4xl mx-auto p-8">
        <p className="text-muted-foreground">読み込み中...</p>
      </div>
    )

  if (isError || !user)
    return (
      <div className="max-w-4xl mx-auto p-8">
        <p className="text-destructive">ユーザーが見つかりません。</p>
      </div>
    )

  const backToList = () => navigate({ to: '/users' })

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-3xl font-bold mb-8">ユーザー編集</h1>
      <UserForm
        key={user.id}
        editingUser={user}
        onSuccess={backToList}
        onCancel={backToList}
      />
    </div>
  )
}
