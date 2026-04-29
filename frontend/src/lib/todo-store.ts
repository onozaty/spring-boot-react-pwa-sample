import { deleteDB, openDB, type DBSchema, type IDBPDatabase } from 'idb'

const DB_NAME = 'pwa-sample'

// React Query で TODO 一覧を識別するキー。
// useTodos / useReachability から共通参照されるため、このストアに併置する。
export const todosQueryKey = ['todos'] as const
export const syncQueueQueryKey = ['sync-queue'] as const

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
  | {
      type: 'create'
      localId: string
      payload: { text: string; done: boolean }
    }
  | {
      type: 'update'
      localId: string
      payload: { serverId: number; text: string; done: boolean }
    }
  | { type: 'delete'; localId: string; payload: { serverId: number } }

export type SyncOpType = SyncOp['type']

// IDB に保存・取り出しされる形。seq は autoIncrement により enqueue 時に IDB が
// 採番する単調増加の整数で、primary key 兼順序キーとして機能する。
// 読み出し側は seq が必ず付与された状態で受け取るためここでは必須型にしている。
// add() 時は seq 未付与のオブジェクトを渡す必要があるが、そのキャストは
// enqueueSyncOp 内で局所化する。
export type SyncQueueRecord = SyncOp & {
  seq: number
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
    key: number
    value: SyncQueueRecord
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

        // seq を keyPath にしつつ autoIncrement で採番させる。
        // primary key 順 = enqueue 順なので追加のインデックスは不要。
        db.createObjectStore('sync-queue', {
          keyPath: 'seq',
          autoIncrement: true,
        })
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

// seq は IDB が autoIncrement で採番するため、ここでは渡さない。
// add() の引数型は seq を要求するが実際の IDB 挙動と異なるためキャストで吸収する。
// 読み出し側 (getPendingSyncOps 等) では seq が必ず存在する状態で取り出される。
export async function enqueueSyncOp(op: SyncOp): Promise<void> {
  const db = await getDB()
  await db.add('sync-queue', op as SyncQueueRecord)
}

export async function dequeueSyncOp(seq: number): Promise<void> {
  const db = await getDB()
  await db.delete('sync-queue', seq)
}

export async function clearSyncQueue(): Promise<void> {
  const db = await getDB()
  await db.clear('sync-queue')
}

// primary key (= seq) 順に返る。enqueue 順と一致する。
export async function getPendingSyncOps(): Promise<SyncQueueRecord[]> {
  const db = await getDB()
  return db.getAll('sync-queue')
}

export async function removeSyncOpsByLocalId(localId: string): Promise<void> {
  const db = await getDB()
  const tx = db.transaction('sync-queue', 'readwrite')
  const all = await tx.store.getAll()
  await Promise.all(
    all
      .filter((op) => op.localId === localId)
      .map((op) => tx.store.delete(op.seq)),
  )
  await tx.done
}

// 未送信の 'create' op の done を更新する。
// オフライン中に追加した TODO (serverId 未採番) のチェックを切り替えるとき、
// update op は積めない (serverId が無い) ので、create op 自体の payload を書き換える。
export async function updatePendingCreateDone(
  localId: string,
  done: boolean,
): Promise<void> {
  const db = await getDB()
  const tx = db.transaction('sync-queue', 'readwrite')
  const all = await tx.store.getAll()
  const targets = all.filter(
    (op): op is SyncQueueRecord & { type: 'create' } =>
      op.type === 'create' && op.localId === localId,
  )
  await Promise.all(
    targets.map((op) =>
      tx.store.put({ ...op, payload: { ...op.payload, done } }),
    ),
  )
  await tx.done
}
