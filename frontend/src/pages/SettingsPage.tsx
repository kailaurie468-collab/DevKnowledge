import { useState, useEffect } from 'react'
import { settingsApi } from '@/api/settings'
import { useAuthStore } from '@/stores/authStore'
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

  if (!isAuthenticated) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-gray-900 mb-4">设置</h1>
        <p className="text-gray-500">请先登录以配置 AI 服务商。</p>
      </div>
    )
  }

  return (
    <div className="max-w-xl">
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
  )
}
