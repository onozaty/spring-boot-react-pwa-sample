import { beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'

// setup.ts で fake-indexeddb/auto がグローバルに登録済み。
// 各テスト前に db.ts のモジュールキャッシュ（dbPromise）を破棄し、
// 新しい IDBFactory インスタンスに差し替えることでテスト間の DB を分離する。
beforeEach(() => {
  vi.resetModules()
  // eslint-disable-next-line no-global-assign
  indexedDB = new IDBFactory()
})

async function getStore() {
  return import('../todo-store')
}

const baseTodo = {
  localId: 'local-1',
  serverId: null,
  userId: 99,
  text: 'テスト TODO',
  done: false,
  updatedAt: '2026-01-01T00:00:00.000Z',
  syncStatus: 'pending' as const,
}

describe('getTodos / upsertTodo / deleteTodo', () => {
  it('upsert した TODO を getTodos で取得できる', async () => {
    // Arrange
    const { upsertTodo, getTodos } = await getStore()

    // Act
    await upsertTodo(baseTodo)
    const todos = await getTodos()

    // Assert
    expect(todos).toHaveLength(1)
    expect(todos[0]).toEqual(baseTodo)
  })

  it('upsert は同じ localId で上書きされる', async () => {
    // Arrange
    const { upsertTodo, getTodos } = await getStore()
    await upsertTodo(baseTodo)

    // Act
    await upsertTodo({ ...baseTodo, text: '更新後テキスト', done: true })
    const todos = await getTodos()

    // Assert
    expect(todos).toHaveLength(1)
    expect(todos[0].text).toBe('更新後テキスト')
    expect(todos[0].done).toBe(true)
  })

  it('複数の TODO を保存・取得できる', async () => {
    // Arrange
    const { upsertTodo, getTodos } = await getStore()

    // Act
    await upsertTodo({ ...baseTodo, localId: 'local-1', text: 'TODO 1' })
    await upsertTodo({ ...baseTodo, localId: 'local-2', text: 'TODO 2' })
    const todos = await getTodos()

    // Assert
    expect(todos).toHaveLength(2)
    expect(todos.map((t) => t.text)).toContain('TODO 1')
    expect(todos.map((t) => t.text)).toContain('TODO 2')
  })

  it('deleteTodo で指定した TODO が削除される', async () => {
    // Arrange
    const { upsertTodo, deleteTodo, getTodos } = await getStore()
    await upsertTodo({ ...baseTodo, localId: 'local-1', text: 'TODO 1' })
    await upsertTodo({ ...baseTodo, localId: 'local-2', text: 'TODO 2' })

    // Act
    await deleteTodo('local-1')
    const todos = await getTodos()

    // Assert
    expect(todos).toHaveLength(1)
    expect(todos[0].localId).toBe('local-2')
  })

  it('存在しない localId を deleteTodo しても例外にならない', async () => {
    // Arrange
    const { deleteTodo } = await getStore()

    // Act & Assert
    await expect(deleteTodo('non-existent')).resolves.toBeUndefined()
  })
})

describe('getTodoByServerId', () => {
  it('serverId でインデックス検索できる', async () => {
    // Arrange
    const { upsertTodo, getTodoByServerId } = await getStore()
    await upsertTodo({ ...baseTodo, serverId: 42 })

    // Act
    const found = await getTodoByServerId(42)

    // Assert
    expect(found).toBeDefined()
    expect(found!.serverId).toBe(42)
    expect(found!.localId).toBe('local-1')
  })

  it('存在しない serverId は undefined を返す', async () => {
    // Arrange
    const { getTodoByServerId } = await getStore()

    // Act
    const found = await getTodoByServerId(999)

    // Assert
    expect(found).toBeUndefined()
  })
})

describe('enqueueSyncOp / getPendingSyncOps / dequeueSyncOp', () => {
  it('enqueue した操作を getPendingSyncOps で取得できる', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps } = await getStore()

    // Act
    await enqueueSyncOp('create', 'local-1', { text: 'テスト' })
    const ops = await getPendingSyncOps()

    // Assert
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('create')
    expect(ops[0].localId).toBe('local-1')
    expect(ops[0].payload).toEqual({ text: 'テスト' })
    expect(ops[0].id).toBeDefined()
    expect(ops[0].createdAt).toBeDefined()
  })

  it('getPendingSyncOps は createdAt 順で返る', async () => {
    // Arrange — 時刻差を確保するため 2ms ずつ間隔を空けて enqueue する
    const { enqueueSyncOp, getPendingSyncOps } = await getStore()
    const wait = (ms: number) =>
      new Promise((resolve) => setTimeout(resolve, ms))

    // Act
    await enqueueSyncOp('create', 'local-1', {})
    await wait(2)
    await enqueueSyncOp('update', 'local-2', {})
    await wait(2)
    await enqueueSyncOp('delete', 'local-3', {})
    const ops = await getPendingSyncOps()

    // Assert
    expect(ops).toHaveLength(3)
    expect(ops[0].type).toBe('create')
    expect(ops[1].type).toBe('update')
    expect(ops[2].type).toBe('delete')
  })

  it('dequeueSyncOp で指定した操作が削除される', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, dequeueSyncOp } = await getStore()
    const wait = (ms: number) =>
      new Promise((resolve) => setTimeout(resolve, ms))
    await enqueueSyncOp('create', 'local-1', {})
    await wait(2)
    await enqueueSyncOp('update', 'local-2', {})
    const [first] = await getPendingSyncOps()

    // Act
    await dequeueSyncOp(first.id)
    const remaining = await getPendingSyncOps()

    // Assert
    expect(remaining).toHaveLength(1)
    expect(remaining[0].localId).toBe('local-2')
  })
})

describe('removeSyncOpsByLocalId', () => {
  it('localId に一致する操作がまとめて削除される', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, removeSyncOpsByLocalId } =
      await getStore()

    // Act
    await enqueueSyncOp('create', 'local-1', {})
    await enqueueSyncOp('update', 'local-1', {})
    await enqueueSyncOp('create', 'local-2', {})
    await removeSyncOpsByLocalId('local-1')
    const ops = await getPendingSyncOps()

    // Assert
    expect(ops).toHaveLength(1)
    expect(ops[0].localId).toBe('local-2')
  })

  it('一致する操作がなくても例外にならない', async () => {
    // Arrange
    const { removeSyncOpsByLocalId } = await getStore()

    // Act & Assert
    await expect(
      removeSyncOpsByLocalId('non-existent'),
    ).resolves.toBeUndefined()
  })
})
