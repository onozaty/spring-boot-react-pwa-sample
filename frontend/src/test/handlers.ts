import { http, HttpResponse } from 'msw'
import type { components } from '@/generated/api'

type User = components['schemas']['User']
type Todo = components['schemas']['Todo']

export const mockTodos: Todo[] = [
  {
    id: 1,
    userId: 99,
    text: '牛乳を買う',
    done: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    userId: 99,
    text: 'メールを返信する',
    done: true,
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
]

export const mockUsers: User[] = [
  {
    id: 1,
    name: '山田太郎',
    email: 'yamada@example.com',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    name: '鈴木花子',
    email: 'suzuki@example.com',
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
]

export const mockAuthUser: User = {
  id: 99,
  name: 'admin',
  email: 'admin@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export const handlers = [
  http.get('*/api/health', () => {
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/auth/me', () => {
    return HttpResponse.json(mockAuthUser)
  }),

  // :id ありのルートを先に登録
  http.get('*/api/users/:id', ({ params }) => {
    const user = mockUsers.find((u) => u.id === Number(params.id))
    if (!user) {
      return new HttpResponse(null, { status: 404 })
    }
    return HttpResponse.json(user)
  }),

  http.get('*/api/users', () => {
    return HttpResponse.json(mockUsers)
  }),

  http.post('*/api/users', async ({ request }) => {
    const body = (await request.json()) as { name: string; email: string }
    const newUser: User = {
      id: 3,
      name: body.name,
      email: body.email,
      createdAt: '2026-01-03T00:00:00Z',
      updatedAt: '2026-01-03T00:00:00Z',
    }
    return HttpResponse.json(newUser, { status: 201 })
  }),

  http.put('*/api/users/:id', async ({ params, request }) => {
    const body = (await request.json()) as { name: string; email: string }
    const user = mockUsers.find((u) => u.id === Number(params.id))
    if (!user) {
      return new HttpResponse(null, { status: 404 })
    }
    return HttpResponse.json({ ...user, ...body })
  }),

  http.delete('*/api/users/:id', ({ params }) => {
    const user = mockUsers.find((u) => u.id === Number(params.id))
    if (!user) {
      return new HttpResponse(null, { status: 404 })
    }
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/todos', () => {
    return HttpResponse.json(mockTodos)
  }),

  http.post('*/api/todos', async ({ request }) => {
    const body = (await request.json()) as { text: string }
    const newTodo: Todo = {
      id: 100,
      userId: 99,
      text: body.text,
      done: false,
      createdAt: '2026-01-03T00:00:00Z',
      updatedAt: '2026-01-03T00:00:00Z',
    }
    return HttpResponse.json(newTodo, { status: 201 })
  }),

  http.put('*/api/todos/:id', async ({ params, request }) => {
    const body = (await request.json()) as { text: string; done: boolean }
    const todo = mockTodos.find((t) => t.id === Number(params.id))
    if (!todo) return new HttpResponse(null, { status: 404 })
    return HttpResponse.json({
      ...todo,
      ...body,
      updatedAt: '2026-01-03T00:00:00Z',
    })
  }),

  http.delete('*/api/todos/:id', ({ params }) => {
    const todo = mockTodos.find((t) => t.id === Number(params.id))
    if (!todo) return new HttpResponse(null, { status: 404 })
    return new HttpResponse(null, { status: 204 })
  }),
]
