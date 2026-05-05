import { useState, useEffect } from 'react'
import { initStorage, checkPermission, cleanupActivities } from '@/storage/LocalActivityStorage'

export function StorageSettings() {
  const [storageMode, setStorageMode] = useState<'local' | 'cloud'>('local')
  const [keepDays, setKeepDays] = useState(30)
  const [dirPermission, setDirPermission] = useState<'unknown' | 'granted' | 'denied'>('unknown')

  useEffect(() => {
    checkPermission().then(ok => {
      setDirPermission(ok ? 'granted' : 'denied')
    })
  }, [])

  const handleSelectDir = async () => {
    const ok = await initStorage()
    setDirPermission(ok ? 'granted' : 'denied')
  }

  const handleCleanup = async () => {
    await cleanupActivities(keepDays)
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <h2 className="text-lg font-bold text-gray-900">数据存储</h2>
      </div>

      <div className="max-w-xl border border-gray-200 rounded-lg p-6">
        <p className="text-sm text-gray-500 mb-4">
          行为数据用于智能推荐 Skills，只存储关键词摘要，不存储原始内容。
        </p>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">存储位置</label>
            <div className="space-y-2">
              <label className="flex items-center gap-3 p-3 border border-gray-200 rounded-lg cursor-pointer hover:bg-gray-50">
                <input
                  type="radio"
                  name="storageMode"
                  value="local"
                  checked={storageMode === 'local'}
                  onChange={() => setStorageMode('local')}
                  className="text-primary-600"
                />
                <div>
                  <span className="text-sm font-medium text-gray-900">本地存储</span>
                  <p className="text-xs text-gray-500">数据保存在本地目录，不上传服务器（推荐）</p>
                </div>
              </label>
              <label className="flex items-center gap-3 p-3 border border-gray-200 rounded-lg cursor-pointer hover:bg-gray-50">
                <input
                  type="radio"
                  name="storageMode"
                  value="cloud"
                  checked={storageMode === 'cloud'}
                  onChange={() => setStorageMode('cloud')}
                  className="text-primary-600"
                />
                <div>
                  <span className="text-sm font-medium text-gray-900">云端同步</span>
                  <p className="text-xs text-gray-500">数据同步到服务器，支持多设备</p>
                </div>
              </label>
            </div>
          </div>

          {storageMode === 'local' && (
            <div className="pl-4 border-l-2 border-primary-200 space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">本地目录</label>
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleSelectDir}
                    className="px-3 py-1.5 border border-gray-300 rounded-md text-sm hover:bg-gray-50"
                  >
                    选择目录
                  </button>
                  <span className={`text-xs ${dirPermission === 'granted' ? 'text-green-600' : dirPermission === 'denied' ? 'text-red-500' : 'text-gray-400'}`}>
                    {dirPermission === 'granted' ? '已授权' : dirPermission === 'denied' ? '未授权' : '未选择'}
                  </span>
                </div>
                <p className="text-xs text-gray-400 mt-1">仅支持 Chrome/Edge，选择后浏览器会记住权限</p>
              </div>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">自动清理</label>
            <select
              value={keepDays}
              onChange={e => setKeepDays(Number(e.target.value))}
              className="px-3 py-2 border border-gray-300 rounded-md text-sm"
            >
              <option value={7}>保留 7 天</option>
              <option value={14}>保留 14 天</option>
              <option value={30}>保留 30 天</option>
              <option value={90}>保留 90 天</option>
            </select>
          </div>

          {storageMode === 'local' && (
            <button
              onClick={handleCleanup}
              className="px-3 py-1.5 border border-gray-300 rounded-md text-sm text-gray-600 hover:bg-gray-50"
            >
              立即清理过期数据
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
