import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/server'
import { client } from '../api-client'

describe('api-client refresh on 401', () => {
  it('POST with body: 401 → refresh → retry succeeds and original body is delivered', async () => {
    let callCount = 0
    let firstBody: unknown = null
    let secondBody: unknown = null
    let refreshCalled = false

    server.use(
      http.post('http://localhost:3000/api/todos', async ({ request }) => {
        callCount += 1
        const body = await request.json()
        if (callCount === 1) {
          firstBody = body
          return new HttpResponse(null, { status: 401 })
        }
        secondBody = body
        return HttpResponse.json(
          {
            id: 1,
            userId: 1,
            text: (body as { text: string }).text,
            done: false,
            createdAt: '2026-04-26T00:00:00Z',
            updatedAt: '2026-04-26T00:00:00Z',
          },
          { status: 201 },
        )
      }),
      http.post('http://localhost:3000/api/auth/refresh', () => {
        refreshCalled = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const result = await client.POST('/api/todos', {
      body: { text: 'hello' },
    })

    expect(callCount).toBe(2)
    expect(refreshCalled).toBe(true)
    expect(firstBody).toEqual({ text: 'hello' })
    expect(secondBody).toEqual({ text: 'hello' })
    expect(result.response.status).toBe(201)
    expect(result.data).toMatchObject({ text: 'hello' })
  })

  it('concurrent 401s share a single refresh call (in-flight de-dup)', async () => {
    let todosCallCount = 0
    let meCallCount = 0
    let refreshCallCount = 0
    // refresh を遅らせて、2 つ目の 401 が onResponse に到達する前に
    // 1 つ目の refresh が完了しないようにする。これが無いと
    // tryRefresh の in-flight 共有を実質的に検証できない。
    let releaseRefresh: (() => void) | null = null
    const refreshGate = new Promise<void>((resolve) => {
      releaseRefresh = resolve
    })

    server.use(
      http.get('http://localhost:3000/api/todos', () => {
        todosCallCount += 1
        if (todosCallCount === 1) {
          return new HttpResponse(null, { status: 401 })
        }
        return HttpResponse.json([])
      }),
      http.get('http://localhost:3000/api/auth/me', () => {
        meCallCount += 1
        if (meCallCount === 1) {
          return new HttpResponse(null, { status: 401 })
        }
        return HttpResponse.json({
          id: 1,
          email: 'user@example.com',
          name: 'user',
        })
      }),
      http.post('http://localhost:3000/api/auth/refresh', async () => {
        refreshCallCount += 1
        await refreshGate
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const todosPromise = client.GET('/api/todos')
    const mePromise = client.GET('/api/auth/me')
    // 両 401 が onResponse に到達してから refresh を解放する。
    await new Promise((r) => setTimeout(r, 10))
    releaseRefresh!()

    const [todosResult, meResult] = await Promise.all([todosPromise, mePromise])

    expect(refreshCallCount).toBe(1)
    expect(todosCallCount).toBe(2)
    expect(meCallCount).toBe(2)
    expect(todosResult.response.status).toBe(200)
    expect(meResult.response.status).toBe(200)
  })

  it('GET (no body): 401 → refresh → retry succeeds', async () => {
    let callCount = 0
    let refreshCalled = false

    server.use(
      http.get('http://localhost:3000/api/todos', () => {
        callCount += 1
        if (callCount === 1) {
          return new HttpResponse(null, { status: 401 })
        }
        return HttpResponse.json([
          {
            id: 1,
            userId: 1,
            text: 'after refresh',
            done: false,
            createdAt: '2026-04-26T00:00:00Z',
            updatedAt: '2026-04-26T00:00:00Z',
          },
        ])
      }),
      http.post('http://localhost:3000/api/auth/refresh', () => {
        refreshCalled = true
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const result = await client.GET('/api/todos')

    expect(callCount).toBe(2)
    expect(refreshCalled).toBe(true)
    expect(result.response.status).toBe(200)
    expect(result.data).toEqual([
      expect.objectContaining({ text: 'after refresh' }),
    ])
  })
})
