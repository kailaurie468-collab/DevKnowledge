import { useState, useEffect } from 'react'
import { settingsApi } from '@/api/settings'
import { useNotify } from '@/stores/notify'
import type { AiConfig, AiConfigRequest, ProviderInfo, TokenUsage } from '@/types/api'

export function AiSettings() {
  const { notify } = useNotify()
  const [providers, setProviders] = useState<ProviderInfo[]>([])
  const [configs, setConfigs] = useState<AiConfig[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)

  // 表单状态
  const [name, setName] = useState('')
  const [provider, setProvider] = useState('openai')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [maxTokens, setMaxTokens] = useState(10000)
  const [maskedKey, setMaskedKey] = useState('')

  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)

  // Token 消耗
  const [tokenUsage, setTokenUsage] = useState<TokenUsage[]>([])

  // 加载数据
  useEffect(() => {
    settingsApi.getProviders().then(setProviders).catch(console.error)
    loadConfigs()
    settingsApi.getTokenUsage().then(setTokenUsage).catch(console.error)
  }, [])

  const loadConfigs = () => {
    settingsApi.getAllConfigs().then(list => {
      setConfigs(list)
      // 默认选中激活配置
      const active = list.find(c => c.isActive)
      if (active && !selectedId) {
        selectConfig(active)
      }
    }).catch(console.error)
  }

  const selectConfig = (config: AiConfig) => {
    setSelectedId(config.id || null)
    setIsNew(false)
    setName(config.name || '')
    setProvider(config.provider)
    setBaseUrl(config.baseUrl)
    setModel(config.model)
    setMaxTokens(config.maxTokens)
    setMaskedKey(config.apiKeyMasked)
    setApiKey('')
    setTestResult(null)
  }

  const handleNew = () => {
    setSelectedId(null)
    setIsNew(true)
    setName('')
    setProvider('openai')
    setApiKey('')
    setBaseUrl('')
    setModel('')
    setMaxTokens(10000)
    setMaskedKey('')
    setTestResult(null)
  }

  // 切换服务商时更新默认 baseUrl
  useEffect(() => {
    if (isNew) {
      const p = providers.find(p => p.name === provider)
      if (p) setBaseUrl(p.defaultBaseUrl)
    }
  }, [provider, providers, isNew])

  const handleSave = async () => {
    setSaving(true)
    try {
      const data: AiConfigRequest = {
        configId: selectedId || undefined,
        name: name || provider,
        provider,
        apiKey,
        baseUrl,
        model,
        maxTokens,
      }
      const saved = await settingsApi.updateAiConfig(data)
      setMaskedKey(saved.apiKeyMasked)
      setApiKey('')
      setIsNew(false)
      setSelectedId(saved.id || null)
      loadConfigs()
      notify('配置已保存', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '保存失败', 'error')
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

  const handleDelete = async () => {
    if (!selectedId) return
    if (!confirm('确定删除此配置？')) return
    try {
      await settingsApi.deleteConfig(selectedId)
      setSelectedId(null)
      setIsNew(true)
      loadConfigs()
      notify('配置已删除', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '删除失败', 'error')
    }
  }

  const handleActivate = async (id: string) => {
    try {
      await settingsApi.switchConfig(id)
      loadConfigs()
      notify('已切换', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '切换失败', 'error')
    }
  }

  const maxTokenValue = tokenUsage.reduce((max, d) => Math.max(max, d.tokens), 0)
  const totalTokens = tokenUsage.reduce((sum, d) => sum + d.tokens, 0)

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <h2 className="text-lg font-bold text-gray-900">AI 配置</h2>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 左侧：我的 AI 列表 */}
        <div className="border border-gray-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-gray-500 mb-3">我的 AI</h3>
          <div className="space-y-2">
            {configs.map(config => (
              <button
                key={config.id}
                onClick={() => { selectConfig(config); handleActivate(config.id!) }}
                className={`w-full text-left p-3 rounded-lg border transition-all ${
                  selectedId === config.id
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900">{config.name || config.provider}</span>
                  {config.isActive && (
                    <span className="text-xs px-2 py-0.5 bg-green-100 text-green-700 rounded-full">使用中</span>
                  )}
                </div>
                <p className="text-xs text-gray-500 mt-1">{config.model}</p>
              </button>
            ))}
            <button
              onClick={handleNew}
              className="w-full p-3 border border-dashed border-gray-300 rounded-lg text-sm text-gray-500 hover:border-primary-400 hover:text-primary-600 transition-colors"
            >
              + 添加新配置
            </button>
          </div>
        </div>

        {/* 右侧：配置详情表单 */}
        <div className="lg:col-span-2 border border-gray-200 rounded-lg p-6">
          <h3 className="text-sm font-medium text-gray-500 mb-4">
            {isNew ? '新建配置' : '配置详情'}
          </h3>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">配置名称</label>
              <input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
            </div>

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
              {maskedKey && !isNew && <p className="text-xs text-gray-400 mb-1">当前: {maskedKey}</p>}
              <input
                type="password"
                value={apiKey}
                onChange={e => setApiKey(e.target.value)}
                placeholder={isNew ? '输入 API Key' : '留空则不更新'}
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
              <input
                type="text"
                value={model}
                onChange={e => setModel(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              />
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
              {!isNew && configs.length > 1 && (
                <button
                  onClick={handleDelete}
                  className="px-4 py-2 border border-red-200 text-red-600 rounded-md text-sm font-medium hover:bg-red-50 ml-auto"
                >
                  删除
                </button>
              )}
            </div>

            {testResult && (
              <div className={`p-3 rounded-md text-sm ${testResult.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
                {testResult.message}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Token 消耗柱状图 */}
      <div className="mt-8 border border-gray-200 rounded-lg p-6">
        <h3 className="text-sm font-medium text-gray-500 mb-1">Token 消耗（近 7 天）</h3>
        <p className="text-xs text-gray-400 mb-4">总计: {totalTokens.toLocaleString()} tokens</p>

        {totalTokens === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">暂无数据</p>
        ) : (
          <div className="flex items-end gap-2 h-40">
            {tokenUsage.map((d, i) => {
              const height = maxTokenValue > 0 ? (d.tokens / maxTokenValue) * 100 : 0
              return (
                <div key={i} className="flex-1 flex flex-col items-center gap-1 group">
                  <span className="text-xs text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    {d.tokens.toLocaleString()}
                  </span>
                  <div className="w-full flex items-end" style={{ height: '120px' }}>
                    <div
                      className="w-full bg-primary-500 rounded-t transition-all group-hover:bg-primary-600"
                      style={{ height: `${Math.max(height, 2)}%` }}
                    />
                  </div>
                  <span className="text-xs text-gray-500">{d.date.slice(5)}</span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
