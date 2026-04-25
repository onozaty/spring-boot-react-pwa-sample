import { useState, type SubmitEventHandler } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { $api } from '@/lib/api-client'
import type { components } from '@/generated/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type User = components['schemas']['User']
type ValidationProblemDetail = components['schemas']['ValidationProblemDetail']

interface Props {
  editingUser: User | null
  onSuccess: () => void
  onCancel: () => void
}

export function UserForm({ editingUser, onSuccess, onCancel }: Props) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(editingUser?.name ?? '')
  const [email, setEmail] = useState(editingUser?.email ?? '')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const invalidateUsers = () => {
    queryClient.invalidateQueries($api.queryOptions('get', '/api/users'))
    onSuccess()
  }

  const handleMutationError = (error: unknown) => {
    const errors = extractFieldErrors(error)
    if (errors) {
      setFieldErrors(errors)
    } else if (isConflictError(error)) {
      setFieldErrors({
        email: 'このメールアドレスはすでに使用されています。',
      })
    } else {
      toast.error(
        editingUser
          ? 'ユーザーの更新に失敗しました。'
          : 'ユーザーの作成に失敗しました。',
      )
    }
  }

  const createMutation = $api.useMutation('post', '/api/users', {
    onSuccess: () => {
      toast.success('ユーザーを作成しました。')
      invalidateUsers()
    },
    onError: handleMutationError,
  })

  const updateMutation = $api.useMutation('put', '/api/users/{id}', {
    onSuccess: () => {
      toast.success('ユーザーを更新しました。')
      invalidateUsers()
    },
    onError: handleMutationError,
  })

  const isPending = createMutation.isPending || updateMutation.isPending

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault()
    if (isPending) return
    setFieldErrors({})
    if (editingUser?.id !== undefined) {
      updateMutation.mutate({
        params: { path: { id: editingUser.id } },
        body: { name, email },
      })
    } else {
      createMutation.mutate({ body: { name, email, password } })
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border rounded-lg p-6 space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="name">名前</Label>
          <Input
            id="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          {fieldErrors.name && (
            <p className="text-destructive text-sm">{fieldErrors.name}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">メールアドレス</Label>
          <Input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          {fieldErrors.email && (
            <p className="text-destructive text-sm">{fieldErrors.email}</p>
          )}
        </div>
      </div>
      {!editingUser && (
        <div className="space-y-2">
          <Label htmlFor="password">パスワード</Label>
          <Input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
          {fieldErrors.password && (
            <p className="text-destructive text-sm">{fieldErrors.password}</p>
          )}
        </div>
      )}
      <div className="flex justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={isPending}
        >
          キャンセル
        </Button>
        <Button type="submit" disabled={isPending}>
          {editingUser ? '更新' : '作成'}
        </Button>
      </div>
    </form>
  )
}

function isConflictError(error: unknown): boolean {
  return (
    error !== null &&
    typeof error === 'object' &&
    'status' in error &&
    (error as { status: unknown }).status === 409
  )
}

function extractFieldErrors(error: unknown): Record<string, string> | null {
  if (
    error === null ||
    typeof error !== 'object' ||
    !('errors' in error) ||
    !Array.isArray((error as ValidationProblemDetail).errors)
  ) {
    return null
  }
  const result: Record<string, string> = {}
  for (const err of (error as ValidationProblemDetail).errors ?? []) {
    if (err.field && err.message) {
      result[err.field] = err.message
    }
  }
  return Object.keys(result).length > 0 ? result : null
}
