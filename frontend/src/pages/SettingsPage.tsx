import { useState, useEffect } from 'react'
import { settingsApi } from '@/api/settings'
import { useAuthStore } from '@/stores/authStore'
import { initStorage, checkPermission, cleanupActivities } from '@/storage/LocalActivityStorage'
import type { AiConfigRequest, ProviderInfo } from '@/types/api'

export function SettingsPage() {
  const { isAuthenticated } = useAuthStore()
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [provider, setProvider] = useState('openai')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [maxTokens, setMaxTokens] = useState(4096)
  const [maskedKey, setMaskedKey] = useState('')
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)

  // 存储配置
  const [storageMode, setStorageMode] = useState<'local' | 'cloud'>('local')
  const [keepDays, setKeepDays] = useState(30)
  const [dirPermission, setDirPermission] = useState<'unknown' | 'granted' | 'denied'>('unknown')

  useEffect(() => {
    settingsApi.getProviders().then(setProviders).catch(console.error)
    if (isAuthenticated) {
      settingsApi.getAiConfig().then(config => {
        setProvider(config.provider)
        setBaseUrl(config.baseUrl)
        setModel(config.model)
        setMaxTokens(config.maxTokens)
        setMaskedKey(config.apiKeyMasked)
      }).catch(() => {})
    }
    // 检查本地目录权限
    checkPermission().then(ok => {
      setDirPermission(ok ? 'granted' : 'denied')
    })
  }, [isAuthenticated])

  useEffect(() => {
    const p = providers.find(p => p.name === provider)
    if (p) setBaseUrl(p.defaultBaseUrl)
    if (providers.find(p => p.name === provider)?.models[0]) {
      setModel(providers.find(p => p.name === provider)!.models[0])
    }
  }, [provider, providers])

  const handleSave = async () => {
    setSaving(true)
    try {
      const data: AiConfigRequest = { provider, apiKey, baseUrl, model, maxTokens }
      await settingsApi.updateAiConfig(data)
      const config = await settingsApi.getAiConfig()
      setMaskedKey(config.apiKeyMasked)
      setApiKey('')
    } catch (err) {
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      const res = await settingsApi.testAiConfig()
      setTestResult(res)
    } catch (err) {
      setTestResult({ success: false, message: err instanceof Error ? err.message : '测试失败' })
    } finally {
      setTesting(false)
    }
  }

  const handleSelectDir = async () => {
    const ok = await initStorage()
    setDirPermission(ok ? 'granted' : 'denied')
  }

  const handleCleanup = async () => {
    await cleanupActivities(keepDays)
  }

  if (!isAuthenticated) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-gray-900 mb-4">设置</h1>
        <p className="text-gray-500">请先登录以配置 AI 服务商。</p>
      </div>
    )
  }

  return (
    <div className="max-w-xl space-y-8">
      {/* AI 配置 */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900 mb-4">AI 配置</h1>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">服务商</label>
            <select
              value={provider}
              onChange={e => setProvider(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            >
              {providers.map(p => (
                <option key={p.name} value={p.name}>{p.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
            {maskedKey && <p className="text-xs text-gray-400 mb-1">当前: {maskedKey}</p>}
            <input
              type="password"
              value={apiKey}
              onChange={e => setApiKey(e.target.value)}
              placeholder="输入新的 API Key"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Base URL</label>
            <input
              type="text"
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">模型</label>
            <select
              value={model}
              onChange={e => setModel(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            >
              {providers.find(p => p.name === provider)?.models.map(m => (
                <option key={m} value={m}>{m}</option>
              ))}
              <option value="">自定义模型...</option>
            </select>
            {!providers.find(p => p.name === provider)?.models.includes(model) && (
              <input
                type="text"
                value={model}
                onChange={e => setModel(e.target.value)}
                placeholder="输入模型名称"
                className="mt-2 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">最大 Token 数</label>
            <input
              type="number"
              value={maxTokens}
              onChange={e => setMaxTokens(Number(e.target.value))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </div>

          <div className="flex gap-3">
            <button
              onClick={handleSave}
              disabled={saving}
              className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50"
            >
              {saving ? '保存中...' : '保存'}
            </button>
            <button
              onClick={handleTest}
              disabled={testing}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium hover:bg-gray-50 disabled:opacity-50"
            >
              {testing ? '测试中...' : '测试连接'}
            </button>
          </div>

          {testResult && (
            <div className={`p-3 rounded-md text-sm ${testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
              {testResult.message}
            </div>
          )}
        </div>
      </div>

      {/* 数据存储配置 */}
      <div>
        <h2 className="text-xl font-bold text-gray-900 mb-4">数据存储</h2>
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
