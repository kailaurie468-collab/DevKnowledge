export interface ActivitySummary {
  type: 'demo_generate' | 'kb_search' | 'link_click' | 'skill_extract' | 'skill_export'
  framework?: string
  keywords: string[]
  language?: string
  resultCount?: number
  ts: number
}

const DB_NAME = 'devknowledge'
const DB_VERSION = 1
const STORE_NAME = 'config'
const DIR_KEY = 'activityDirHandle'

let dirHandle: FileSystemDirectoryHandle | null = null

function openConfigDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE_NAME)) {
        req.result.createObjectStore(STORE_NAME)
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function dbGet(key: string): Promise<unknown> {
  const db = await openConfigDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const req = store.get(key)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function dbPut(key: string, value: unknown): Promise<void> {
  const db = await openConfigDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const req = store.put(value, key)
    req.onsuccess = () => resolve()
    req.onerror = () => reject(req.error)
  })
}

function dateStr(daysAgo = 0): string {
  return new Date(Date.now() - daysAgo * 86400000).toISOString().slice(0, 10)
}

export async function initStorage(): Promise<boolean> {
  try {
    dirHandle = await window.showDirectoryPicker({ mode: 'readwrite' })
    await dbPut(DIR_KEY, dirHandle)
    return true
  } catch {
    return false
  }
}

export async function restoreStorage(): Promise<boolean> {
  try {
    const handle = await dbGet(DIR_KEY) as FileSystemDirectoryHandle | undefined
    if (!handle) return false
    const perm = await handle.queryPermission({ mode: 'readwrite' })
    if (perm === 'granted') {
      dirHandle = handle
      return true
    }
    const req = await handle.requestPermission({ mode: 'readwrite' })
    if (req === 'granted') {
      dirHandle = handle
      return true
    }
    return false
  } catch {
    return false
  }
}

export async function checkPermission(): Promise<boolean> {
  if (dirHandle) {
    return (await dirHandle.queryPermission({ mode: 'readwrite' })) === 'granted'
  }
  return restoreStorage()
}

async function ensureHandle(): Promise<FileSystemDirectoryHandle | null> {
  if (dirHandle) return dirHandle
  const ok = await restoreStorage()
  return ok ? dirHandle : null
}

export async function recordActivity(activity: ActivitySummary): Promise<void> {
  const handle = await ensureHandle()
  if (!handle) return
  try {
    const fileName = `activities-${dateStr()}.jsonl`
    const fileHandle = await handle.getFileHandle(fileName, { create: true })
    const file = await fileHandle.getFile()
    const writable = await fileHandle.createWritable({ keepExistingData: true })
    await writable.seek(file.size)
    await writable.write(JSON.stringify(activity) + '\n')
    await writable.close()
  } catch (err) {
    console.error('Failed to write activity:', err)
  }
}

export async function readRecentActivities(days: number): Promise<ActivitySummary[]> {
  const handle = await ensureHandle()
  if (!handle) return []
  const activities: ActivitySummary[] = []
  for (let i = 0; i < days; i++) {
    try {
      const fileHandle = await handle.getFileHandle(`activities-${dateStr(i)}.jsonl`)
      const file = await fileHandle.getFile()
      const text = await file.text()
      text.trim().split('\n').forEach(line => {
        if (line) {
          try { activities.push(JSON.parse(line)) } catch { /* skip */ }
        }
      })
    } catch { /* file not found */ }
  }
  return activities
}

export async function cleanupActivities(keepDays: number): Promise<void> {
  const handle = await ensureHandle()
  if (!handle) return
  const cutoff = dateStr(keepDays)
  for await (const entry of handle.values()) {
    if (entry.name.startsWith('activities-') && entry.name.endsWith('.jsonl')) {
      const date = entry.name.replace('activities-', '').replace('.jsonl', '')
      if (date < cutoff) await handle.removeEntry(entry.name)
    }
  }
}
