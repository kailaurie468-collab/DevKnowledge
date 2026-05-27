import { useState, useEffect } from 'react'
import { embeddingApi } from '@/api/embedding'
import { useNotify } from '@/stores/notify'
import type { EmbeddingConfig, EmbeddingConfigRequest, TokenUsage } from '@/types/api'

export function EmbeddingSettings() {
  const { notify } = useNotify()
  const [configs, setConfigs] = useState<EmbeddingConfig[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)

  const [name, setName] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('https://api.openai.com/v1')
  const [maskedKey, setMaskedKey] = useState('')

  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [tokenUsage, setTokenUsage] = useState<TokenUsage[]>([])

  useEffect(() => {
    loadConfigs()
    embeddingApi.getTokenUsage().then(setTokenUsage).catch(console.error)
  }, [])

  const loadConfigs = () => {
    embeddingApi.getAllConfigs().then(list => {
      setConfigs(list)
      if (!selectedId) {
        const active = list.find(c => c.isActive) || list[0]
        if (active) selectConfig(active)
      }
    }).catch(console.error)
  }

  const selectConfig = (config: EmbeddingConfig) => {
    setSelectedId(config.id || null)
    setIsNew(false)
    setName(config.name || '')
    setBaseUrl(config.baseUrl)
    setMaskedKey(config.apiKeyMasked)
    setApiKey('')
    setTestResult(null)
  }

  const handleNew = () => {
    setSelectedId(null)
    setIsNew(true)
    setName('')
    setBaseUrl('https://api.openai.com/v1')
    setApiKey('')
    setMaskedKey('')
    setTestResult(null)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const data: EmbeddingConfigRequest = {
        configId: selectedId || undefined,
        name: name || 'OpenAI Embedding',
        apiKey,
        baseUrl,
      }
      const saved = await embeddingApi.updateConfig(data)
      setMaskedKey(saved.apiKeyMasked)
      setApiKey('')
      setIsNew(false)
      setSelectedId(saved.id || null)
      loadConfigs()
      notify('Embedding 配置已保存', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    setTesting(true)
    try {
      const res = await embeddingApi.testConfig()
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
      await embeddingApi.deleteConfig(selectedId)
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
      await embeddingApi.switchConfig(id)
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
        <h2 className="text-lg font-bold text-gray-900">Embedding AI 配置</h2>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 左侧：配置列表 */}
        <div className="border border-gray-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-gray-500 mb-3">我的 Embedding</h3>
          <div className="space-y-2">
            {configs.map(config => (
              <button
                key={config.id}
                onClick={() => selectConfig(config)}
                className={`w-full text-left p-3 rounded-lg border transition-all ${
                  selectedId === config.id
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900">{config.name || 'OpenAI Embedding'}</span>
                  {config.isActive && (
                    <span className="text-xs px-2 py-0.5 bg-green-100 text-green-700 rounded-full">使用中</span>
                  )}
                </div>
                <p className="text-xs text-gray-500 mt-1">{config.baseUrl}</p>
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

        {/* 右侧：配置表单 */}
        <div className="lg:col-span-2 border border-gray-200 rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-gray-500">
              {isNew ? '新建配置' : '配置详情'}
            </h3>
            {isNew && configs.length > 0 && (
              <button
                onClick={() => { setIsNew(false); const first = configs.find(c => c.isActive) || configs[0]; if (first) selectConfig(first) }}
                className="text-xs text-gray-400 hover:text-gray-600 transition-colors"
              >
                ← 返回
              </button>
            )}
          </div>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">配置名称</label>
              <input type="text" value={name} onChange={e => setName(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">API Base URL</label>
              <input type="text" value={baseUrl} onChange={e => setBaseUrl(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">API Key</label>
              {maskedKey && !isNew && <p className="text-xs text-gray-400 mb-1">当前: {maskedKey}</p>}
              <input type="password" value={apiKey} onChange={e => setApiKey(e.target.value)}
                placeholder={isNew ? '输入 OpenAI API Key' : '留空则不更新'}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm" />
            </div>

            <div className="bg-gray-50 rounded-md p-3 text-xs text-gray-500">
              <p className="font-medium text-gray-700 mb-1">最佳实践</p>
              <p>- 推荐 text-embedding-3-small + dimensions=512，兼顾成本和质量</p>
              <p>- 批量处理：系统每 20 个文本片段一次 API 调用</p>
              <p>- 模型和维度在创建知识库时选择，此处仅管理 API 凭证</p>
            </div>

            <div className="flex gap-3">
              <button onClick={handleSave} disabled={saving}
                className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50">
                {saving ? '保存中...' : '保存'}
              </button>
              <button onClick={handleTest} disabled={testing || isNew}
                title={isNew ? '请先保存配置后再测试' : ''}
                className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed">
                {testing ? '测试中...' : '测试连接'}
              </button>
              {!isNew && selectedId && !configs.find(c => c.id === selectedId)?.isActive && (
                <button onClick={() => handleActivate(selectedId!)}
                  className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium hover:bg-gray-50">
                  设为默认
                </button>
              )}
              {!isNew && configs.length > 1 && (
                <button onClick={handleDelete}
                  className="px-4 py-2 border border-red-200 text-red-600 rounded-md text-sm font-medium hover:bg-red-50 ml-auto">
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
        <h3 className="text-sm font-medium text-gray-500 mb-1">Embedding Token 消耗（近 7 天）</h3>
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
                    <div className="w-full bg-primary-500 rounded-t transition-all group-hover:bg-primary-600"
                      style={{ height: `${Math.max(height, 2)}%` }} />
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
