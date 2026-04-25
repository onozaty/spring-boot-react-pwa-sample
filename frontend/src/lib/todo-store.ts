import {
  getDB,
  type SyncOpType,
  type SyncQueueRecord,
  type TodoRecord,
} from './db'

export async function getTodos(): Promise<TodoRecord[]> {
  const db = await getDB()
  return db.getAll('todos')
}

export async function upsertTodo(todo: TodoRecord): Promise<void> {
  const db = await getDB()
  await db.put('todos', todo)
}

export async function deleteTodo(localId: string): Promise<void> {
  const db = await getDB()
  await db.delete('todos', localId)
}

export async function getTodoByServerId(
  serverId: number,
): Promise<TodoRecord | undefined> {
  const db = await getDB()
  return db.getFromIndex('todos', 'byServerId', serverId)
}

export async function enqueueSyncOp(
  type: SyncOpType,
  localId: string,
  payload: Record<string, unknown>,
): Promise<void> {
  const db = await getDB()
  const op: SyncQueueRecord = {
    id: crypto.randomUUID(),
    type,
    payload,
    localId,
    createdAt: new Date().toISOString(),
  }
  await db.put('sync-queue', op)
}

export async function dequeueSyncOp(id: string): Promise<void> {
  const db = await getDB()
  await db.delete('sync-queue', id)
}

export async function getPendingSyncOps(): Promise<SyncQueueRecord[]> {
  const db = await getDB()
  return db.getAllFromIndex('sync-queue', 'byCreatedAt')
}

export async function removeSyncOpsByLocalId(localId: string): Promise<void> {
  const db = await getDB()
  const all = await db.getAll('sync-queue')
  const tx = db.transaction('sync-queue', 'readwrite')
  await Promise.all(
    all
      .filter((op) => op.localId === localId)
      .map((op) => tx.store.delete(op.id)),
  )
  await tx.done
}
