import { deleteDB, openDB, type DBSchema, type IDBPDatabase } from 'idb'

const DB_NAME = 'pwa-sample'

// React Query で TODO 一覧を識別するキー。
// useTodos / useReachability から共通参照されるため、このストアに併置する。
export const todosQueryKey = ['todos'] as const

export type SyncStatus = 'synced' | 'pending'

export interface TodoRecord {
  localId: string
  serverId: number | null
  userId: number
  text: string
  done: boolean
  updatedAt: string
  syncStatus: SyncStatus
}

// sync-queue に積む操作。type ごとに payload の形が決まる discriminated union。
// 取り出し側は `switch (op.type)` で payload 型が自動的に絞り込まれる。
export type SyncOp =
  | { type: 'create'; localId: string; payload: { text: string } }
  | {
      type: 'update'
      localId: string
      payload: { serverId: number; text: string; done: boolean }
    }
  | { type: 'delete'; localId: string; payload: { serverId: number } }

export type SyncOpType = SyncOp['type']

// IDB に保存する形 (id と createdAt は enqueue 時に付与)。
export type SyncQueueRecord = SyncOp & {
  id: string
  createdAt: string
}

interface PwaSampleDB extends DBSchema {
  todos: {
    key: string
    value: TodoRecord
    indexes: {
      byServerId: number
      bySyncStatus: string
    }
  }
  'sync-queue': {
    key: string
    value: SyncQueueRecord
    indexes: {
      byCreatedAt: string
    }
  }
}

let dbPromise: Promise<IDBPDatabase<PwaSampleDB>> | null = null

function getDB(): Promise<IDBPDatabase<PwaSampleDB>> {
  if (!dbPromise) {
    dbPromise = openDB<PwaSampleDB>(DB_NAME, 1, {
      upgrade(db) {
        const todoStore = db.createObjectStore('todos', { keyPath: 'localId' })
        todoStore.createIndex('byServerId', 'serverId')
        todoStore.createIndex('bySyncStatus', 'syncStatus')

        const queueStore = db.createObjectStore('sync-queue', { keyPath: 'id' })
        queueStore.createIndex('byCreatedAt', 'createdAt')
      },
    })
  }
  return dbPromise
}

export async function resetDB(): Promise<void> {
  if (dbPromise) {
    const db = await dbPromise
    db.close()
    dbPromise = null
  }
  await deleteDB(DB_NAME)
}

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

// 受け取る op は discriminated union なので、type ごとに payload の形が型レベルで保証される。
export async function enqueueSyncOp(op: SyncOp): Promise<void> {
  const db = await getDB()
  const record: SyncQueueRecord = {
    ...op,
    id: crypto.randomUUID(),
    createdAt: new Date().toISOString(),
  }
  await db.put('sync-queue', record)
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
