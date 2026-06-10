import { useState, useEffect } from 'react'
import { embeddingApi } from '@/api/embedding'
import { kbApi } from '@/api/kb'
import { useNotify } from '@/stores/notify'
import type { EmbeddingConfig, EmbeddingConfigRequest, TokenUsage, KnowledgeBase } from '@/types/api'

export function EmbeddingSettings() {
  const { notify } = useNotify()
  const [configs, setConfigs] = useState<EmbeddingConfig[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([])

  const [name, setName] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('https://api.openai.com/v1')
  const [modelName, setModelName] = useState('text-embedding-3-small')
  const [maskedKey, setMaskedKey] = useState('')

  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [tokenUsage, setTokenUsage] = useState<TokenUsage[]>([])

  useEffect(() => {
    loadConfigs()
    loadKnowledgeBases()
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

  const loadKnowledgeBases = () => {
    kbApi.getKbs().then(list => {
      setKnowledgeBases(list)
    }).catch(console.error)
  }

  const selectConfig = async (config: EmbeddingConfig) => {
    setSelectedId(config.id || null)
    setIsNew(false)
    setName(config.name || '')
    setBaseUrl(config.baseUrl)
    setModelName(config.modelName || 'text-embedding-3-small')
    setMaskedKey(config.apiKeyMasked)
    setApiKey('')
    setTestResult(null)
    // 点击列表项直接激活该配置
    if (config.id && !config.isActive) {
      try {
        await embeddingApi.switchConfig(config.id)
        loadConfigs()
      } catch (err) {
        console.error('切换失败:', err)
      }
    }
  }

  const handleNew = () => {
    setSelectedId(null)
    setIsNew(true)
    setName('')
    setBaseUrl('https://api.openai.com/v1')
    setModelName('text-embedding-3-small')
    setApiKey('')
    setMaskedKey('')
    setTestResult(null)
  }

  const handleSave = async () => {
    // 验证必填项
    if (!name.trim()) { notify('请输入配置名称', 'error'); return }
    if (!modelName.trim()) { notify('请输入 Embedding 模型名', 'error'); return }
    if (!baseUrl.trim()) { notify('请输入 API Base URL', 'error'); return }
    if (!apiKey.trim() && isNew) { notify('请输入 API Key', 'error'); return }

    setSaving(true)
    try {
      const data: EmbeddingConfigRequest = {
        configId: selectedId || undefined,
        name: name.trim(),
        apiKey,
        baseUrl: baseUrl.trim(),
        modelName: modelName.trim(),
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

  // 获取关联的知识库
  const linkedKBs = knowledgeBases.filter(kb => kb.userId === configs.find(c => c.id === selectedId)?.id)

  const maxTokenValue = tokenUsage.reduce((max, d) => Math.max(max, d.tokens), 0)
  const totalTokens = tokenUsage.reduce((sum, d) => sum + d.tokens, 0)

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">Embedding AI 配置</h2>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 左侧：配置列表 */}
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-white dark:bg-gray-800">
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">我的 Embedding</h3>
          <div className="space-y-2">
            {configs.map(config => (
              <div
                key={config.id}
                className={`flex items-center p-3 rounded-lg border transition-all ${
                  selectedId === config.id
                    ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/30'
                    : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                }`}
              >
                <button
                  onClick={() => selectConfig(config)}
                  className="flex-1 text-left"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{config.name || 'OpenAI Embedding'}</span>
                    {config.isActive && (
                      <span className="text-xs px-2 py-0.5 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 rounded-full">使用中</span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{config.baseUrl}</p>
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    if (confirm(`确定删除配置「${config.name || 'OpenAI Embedding'}」？`)) {
                      embeddingApi.deleteConfig(config.id!).then(() => {
                        if (selectedId === config.id) {
                          setSelectedId(null)
                          setIsNew(true)
                        }
                        loadConfigs()
                        notify('配置已删除', 'success')
                      }).catch(err => {
                        notify(err instanceof Error ? err.message : '删除失败', 'error')
                      })
                    }
                  }}
                  className="ml-2 p-1 text-gray-400 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                  title="删除配置"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            ))}
            <button
              onClick={handleNew}
              className="w-full p-3 border border-dashed border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-500 dark:text-gray-400 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
            >
              + 添加新配置
            </button>
          </div>
        </div>

        {/* 右侧：配置详情 */}
        <div className="lg:col-span-2 border border-gray-200 dark:border-gray-700 rounded-lg p-6 bg-white dark:bg-gray-800">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">
              {isNew ? '新建配置' : '配置详情'}
            </h3>
            {isNew && configs.length > 0 && (
              <button
                onClick={() => { setIsNew(false); const first = configs.find(c => c.isActive) || configs[0]; if (first) selectConfig(first) }}
                className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              >
                ← 返回
              </button>
            )}
          </div>

          {isNew ? (
            /* 新建配置表单 */
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">配置名称</label>
                <input type="text" value={name} onChange={e => setName(e.target.value)}
                  placeholder="My Embedding AI"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Embedding 模型</label>
                <input type="text" value={modelName} onChange={e => setModelName(e.target.value)}
                  placeholder="如：text-embedding-3-small"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">常见模型：text-embedding-3-small、text-embedding-3-large、text-embedding-ada-002</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">API Base URL</label>
                <input type="text" value={baseUrl} onChange={e => setBaseUrl(e.target.value)}
                  placeholder="https://api.openai.com/v1"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">不含端点，如 https://api.openai.com/v1</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">API Key</label>
                <input type="password" value={apiKey} onChange={e => setApiKey(e.target.value)}
                  placeholder="sk-..."
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
              </div>
              <div className="flex gap-3">
                <button onClick={handleSave} disabled={saving}
                  className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50">
                  {saving ? '保存中...' : '保存'}
                </button>
              </div>
            </div>
          ) : selectedId ? (
            /* 配置详情（只读） */
            <div className="space-y-6">
              {/* 基本信息 */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">配置名称</p>
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{configs.find(c => c.id === selectedId)?.name || 'OpenAI Embedding'}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">Embedding 模型</p>
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{configs.find(c => c.id === selectedId)?.modelName || '未指定'}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">API Base URL</p>
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{configs.find(c => c.id === selectedId)?.baseUrl}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">API Key</p>
                  <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{maskedKey}</p>
                </div>
                <div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">状态</p>
                  <p className="text-sm font-medium">
                    {configs.find(c => c.id === selectedId)?.isActive ? (
                      <span className="text-green-600 dark:text-green-400">使用中</span>
                    ) : (
                      <span className="text-gray-500 dark:text-gray-400">未激活</span>
                    )}
                  </p>
                </div>
              </div>

              {/* 说明 */}
              <div className="bg-blue-50 dark:bg-blue-900/20 rounded-md p-3 text-xs text-blue-700 dark:text-blue-300">
                <p className="font-medium mb-1">💡 关于 Embedding 模型</p>
                <p>Embedding 模型在创建知识库时选择，此处仅管理 API 凭证。</p>
                <p>不同知识库可以使用不同的 Embedding 模型（需在同一 API 下）。</p>
              </div>

              {/* 关联的知识库 */}
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">关联的知识库</p>
                {knowledgeBases.length > 0 ? (
                  <div className="space-y-2">
                    {knowledgeBases.map(kb => (
                      <div key={kb.id} className="flex items-center justify-between p-2 bg-gray-50 dark:bg-gray-700 rounded-md">
                        <div>
                          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{kb.name}</p>
                          <p className="text-xs text-gray-500 dark:text-gray-400">模型: {kb.embeddingModel || '未指定'}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-gray-400 dark:text-gray-500">暂无关联的知识库</p>
                )}
              </div>

              {/* 操作按钮 */}
              <div className="flex gap-3">
                <button onClick={handleTest} disabled={testing}
                  className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 disabled:opacity-50">
                  {testing ? '测试中...' : '测试连接'}
                </button>
              </div>

              {testResult && (
                <div className={`p-3 rounded-md text-sm ${testResult.success ? 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400' : 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                  {testResult.message}
                </div>
              )}
            </div>
          ) : (
            <p className="text-sm text-gray-400 dark:text-gray-500">请选择或创建一个配置</p>
          )}
        </div>
      </div>

      {/* Token 消耗柱状图 */}
      <div className="mt-8 border border-gray-200 dark:border-gray-700 rounded-lg p-6 bg-white dark:bg-gray-800">
        <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">Embedding Token 消耗（近 7 天）</h3>
        <p className="text-xs text-gray-400 dark:text-gray-500 mb-4">总计: {totalTokens.toLocaleString()} tokens</p>
        {totalTokens === 0 ? (
          <p className="text-sm text-gray-400 dark:text-gray-500 py-8 text-center">暂无数据</p>
        ) : (
          <div className="flex items-end gap-2 h-40">
            {tokenUsage.map((d, i) => {
              const height = maxTokenValue > 0 ? (d.tokens / maxTokenValue) * 100 : 0
              return (
                <div key={i} className="flex-1 flex flex-col items-center gap-1 group">
                  <span className="text-xs text-gray-400 dark:text-gray-500 opacity-0 group-hover:opacity-100 transition-opacity">
                    {d.tokens.toLocaleString()}
                  </span>
                  <div className="w-full flex items-end" style={{ height: '120px' }}>
                    <div className="w-full bg-primary-500 rounded-t transition-all group-hover:bg-primary-600"
                      style={{ height: `${Math.max(height, 2)}%` }} />
                  </div>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{d.date.slice(5)}</span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
