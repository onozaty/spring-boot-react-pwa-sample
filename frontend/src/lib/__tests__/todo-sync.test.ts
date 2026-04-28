import { http, HttpResponse } from 'msw'
import { IDBFactory } from 'fake-indexeddb'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../test/server'

// 各テスト前にモジュールキャッシュ (todo-store の dbPromise) を破棄し、
// 新しい IDBFactory に差し替えてテスト間の DB 状態を分離する。
// todo-store.test.ts と同じ方式。
beforeEach(() => {
  vi.resetModules()
  // eslint-disable-next-line no-global-assign
  indexedDB = new IDBFactory()
})

async function getModules() {
  const store = await import('../todo-store')
  const sync = await import('../todo-sync')
  return { ...store, ...sync }
}

// MSW で /api/todos POST をハンドルし、受け取ったリクエストボディを記録する。
// サーバーは body の done をそのまま採用する (修正後のバックエンド挙動)。
type CreateBody = { text: string; done?: boolean }
function setupCreateTodoEndpoint(): { calls: CreateBody[] } {
  const calls: CreateBody[] = []
  server.use(
    http.post('*/api/todos', async ({ request }) => {
      const body = (await request.json()) as CreateBody
      calls.push(body)
      return HttpResponse.json(
        {
          id: 555,
          userId: 99,
          text: body.text,
          done: body.done ?? false,
          createdAt: '2026-04-27T00:00:01Z',
          updatedAt: '2026-04-27T00:00:01Z',
        },
        { status: 201 },
      )
    }),
  )
  return { calls }
}

describe('processSyncQueue: オフライン中の create + toggle 後にオンライン復帰', () => {
  it('オフラインで追加してチェックした TODO は、オンライン復帰後も done=true で同期される', async () => {
    // Arrange: オフラインで「追加 → チェック」した直後の状態を再現する。
    // use-todos.ts の挙動どおり:
    //   1. 'create' op を done=false で enqueue
    //   2. updatePendingCreateDone(localId, true) で create op の done を true に書き換え
    //   3. ローカル TODO は done=true で upsert
    const {
      upsertTodo,
      enqueueSyncOp,
      updatePendingCreateDone,
      getTodos,
      getPendingSyncOps,
      processSyncQueue,
    } = await getModules()
    const localId = 'local-offline-1'
    await enqueueSyncOp({
      type: 'create',
      localId,
      payload: { text: '牛乳を買う', done: false },
    })
    await updatePendingCreateDone(localId, true)
    await upsertTodo({
      localId,
      serverId: null,
      userId: 0,
      text: '牛乳を買う',
      done: true,
      updatedAt: '2026-04-27T00:00:00.000Z',
      syncStatus: 'pending',
    })
    const { calls } = setupCreateTodoEndpoint()

    // Act
    await processSyncQueue()

    // Assert: サーバーには done=true で送られ、IDB も done=true を保持
    expect(calls).toEqual([{ text: '牛乳を買う', done: true }])
    const todos = await getTodos()
    expect(todos).toHaveLength(1)
    expect(todos[0].serverId).toBe(555)
    expect(todos[0].done).toBe(true)
    expect(await getPendingSyncOps()).toHaveLength(0)
  })

  it('オフラインで追加→チェック→外した TODO は、オンライン復帰後 done=false で同期される', async () => {
    // Arrange: 追加 → チェック → 外す、と切り替えた最終状態を再現する
    const {
      upsertTodo,
      enqueueSyncOp,
      updatePendingCreateDone,
      getTodos,
      processSyncQueue,
    } = await getModules()
    const localId = 'local-offline-2'
    await enqueueSyncOp({
      type: 'create',
      localId,
      payload: { text: 'メモ', done: false },
    })
    await updatePendingCreateDone(localId, true)
    await updatePendingCreateDone(localId, false)
    await upsertTodo({
      localId,
      serverId: null,
      userId: 0,
      text: 'メモ',
      done: false,
      updatedAt: '2026-04-27T00:00:00.000Z',
      syncStatus: 'pending',
    })
    const { calls } = setupCreateTodoEndpoint()

    // Act
    await processSyncQueue()

    // Assert
    expect(calls).toEqual([{ text: 'メモ', done: false }])
    const todos = await getTodos()
    expect(todos[0].done).toBe(false)
  })
})
