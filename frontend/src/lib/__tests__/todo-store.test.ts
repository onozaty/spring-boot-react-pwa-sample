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
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 'テスト', done: false },
    })
    const ops = await getPendingSyncOps()

    // Assert
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('create')
    expect(ops[0].localId).toBe('local-1')
    expect(ops[0].payload).toEqual({ text: 'テスト', done: false })
    expect(ops[0].seq).toBeGreaterThan(0)
  })

  it('getPendingSyncOps は enqueue 順 (seq 昇順) で返る', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps } = await getStore()

    // Act
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })
    await enqueueSyncOp({
      type: 'update',
      localId: 'local-2',
      payload: { serverId: 2, text: 't2', done: false },
    })
    await enqueueSyncOp({
      type: 'delete',
      localId: 'local-3',
      payload: { serverId: 3 },
    })
    const ops = await getPendingSyncOps()

    // Assert
    expect(ops).toHaveLength(3)
    expect(ops[0].type).toBe('create')
    expect(ops[1].type).toBe('update')
    expect(ops[2].type).toBe('delete')
    // seq は単調増加していること
    expect(ops[0].seq).toBeLessThan(ops[1].seq)
    expect(ops[1].seq).toBeLessThan(ops[2].seq)
  })

  it('dequeueSyncOp で指定した操作が削除される', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, dequeueSyncOp } = await getStore()
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })
    await enqueueSyncOp({
      type: 'update',
      localId: 'local-2',
      payload: { serverId: 2, text: 't2', done: false },
    })
    const [first] = await getPendingSyncOps()

    // Act
    await dequeueSyncOp(first.seq)
    const remaining = await getPendingSyncOps()

    // Assert
    expect(remaining).toHaveLength(1)
    expect(remaining[0].localId).toBe('local-2')
  })

  it('dequeue 後に enqueue した op の seq は前のレコードと衝突しない', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, dequeueSyncOp } = await getStore()
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-2',
      payload: { text: 't2', done: false },
    })
    const [first, second] = await getPendingSyncOps()
    await dequeueSyncOp(first.seq)

    // Act
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-3',
      payload: { text: 't3', done: false },
    })

    // Assert — autoIncrement が単調増加するので、新 op の seq は既存より大きい
    const ops = await getPendingSyncOps()
    expect(ops).toHaveLength(2)
    expect(ops[0].localId).toBe('local-2')
    expect(ops[1].localId).toBe('local-3')
    expect(ops[1].seq).toBeGreaterThan(second.seq)
  })
})

describe('updatePendingCreateDone', () => {
  it('指定 localId の create op の done が書き換わる', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, updatePendingCreateDone } =
      await getStore()
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })

    // Act
    await updatePendingCreateDone('local-1', true)
    const ops = await getPendingSyncOps()

    // Assert
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('create')
    expect(ops[0].payload).toEqual({ text: 't1', done: true })
  })

  it('別 localId の create op は書き換わらない', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, updatePendingCreateDone } =
      await getStore()
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-2',
      payload: { text: 't2', done: false },
    })

    // Act
    await updatePendingCreateDone('local-1', true)
    const ops = await getPendingSyncOps()

    // Assert
    const t1 = ops.find((op) => op.localId === 'local-1')
    const t2 = ops.find((op) => op.localId === 'local-2')
    expect(t1?.payload).toEqual({ text: 't1', done: true })
    expect(t2?.payload).toEqual({ text: 't2', done: false })
  })

  it('一致する create op がなくても例外にならない', async () => {
    // Arrange
    const { updatePendingCreateDone } = await getStore()

    // Act & Assert
    await expect(
      updatePendingCreateDone('non-existent', true),
    ).resolves.toBeUndefined()
  })
})

describe('resetDB', () => {
  it('resetDB 後に enqueue した op の seq は 1 から振り直される', async () => {
    // Arrange — IDB の autoIncrement カウンタを進めた状態を作る
    const { enqueueSyncOp, getPendingSyncOps, resetDB } = await getStore()
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-2',
      payload: { text: 't2', done: false },
    })

    // Act
    await resetDB()
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-after-reset',
      payload: { text: 'after', done: false },
    })
    const ops = await getPendingSyncOps()

    // Assert — IDB ごと削除されるので、autoIncrement のカウンタも 1 に戻る
    expect(ops).toHaveLength(1)
    expect(ops[0].seq).toBe(1)
  })
})

describe('removeSyncOpsByLocalId', () => {
  it('localId に一致する操作がまとめて削除される', async () => {
    // Arrange
    const { enqueueSyncOp, getPendingSyncOps, removeSyncOpsByLocalId } =
      await getStore()

    // Act
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-1',
      payload: { text: 't1', done: false },
    })
    await enqueueSyncOp({
      type: 'update',
      localId: 'local-1',
      payload: { serverId: 1, text: 't1u', done: true },
    })
    await enqueueSyncOp({
      type: 'create',
      localId: 'local-2',
      payload: { text: 't2', done: false },
    })
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
