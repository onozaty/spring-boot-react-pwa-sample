import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { OfflineUnavailable } from '@/components/offline-unavailable'
import { useReachability } from '@/hooks/use-reachability'
import { UserForm } from '@/components/user-form'

export const Route = createFileRoute('/users/new')({
  component: NewUserPageGuard,
})

function NewUserPageGuard() {
  const isReachable = useReachability()
  if (!isReachable) return <OfflineUnavailable />
  return <NewUserPage />
}

function NewUserPage() {
  const navigate = useNavigate()
  const backToList = () => navigate({ to: '/users' })

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-3xl font-bold mb-8">ユーザー作成</h1>
      <UserForm
        editingUser={null}
        onSuccess={backToList}
        onCancel={backToList}
      />
    </div>
  )
}
