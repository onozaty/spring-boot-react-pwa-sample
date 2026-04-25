import { deleteDB, openDB, type DBSchema, type IDBPDatabase } from 'idb'

const DB_NAME = 'pwa-sample'

export type SyncStatus = 'synced' | 'pending'
export type SyncOpType = 'create' | 'update' | 'delete'

export interface TodoRecord {
  localId: string
  serverId: number | null
  userId: number
  text: string
  done: boolean
  updatedAt: string
  syncStatus: SyncStatus
}

export interface SyncQueueRecord {
  id: string
  type: SyncOpType
  payload: Record<string, unknown>
  localId: string
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

export function getDB(): Promise<IDBPDatabase<PwaSampleDB>> {
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
