import { useState, useEffect } from 'react'
import { rerankerApi } from '@/api/reranker'
import { useNotify } from '@/stores/notify'
import type { RerankerConfig, RerankerConfigRequest } from '@/types/api'

export function RerankerSettings() {
  const { notify } = useNotify()
  const [configs, setConfigs] = useState<RerankerConfig[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [isNew, setIsNew] = useState(false)

  const [name, setName] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('https://api.openai.com/v1')
  const [model, setModel] = useState('')
  const [maskedKey, setMaskedKey] = useState('')

  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [testing, setTesting] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    loadConfigs()
  }, [])

  const loadConfigs = () => {
    rerankerApi.getAllConfigs().then(list => {
      setConfigs(list)
      if (!selectedId) {
        const active = list.find(c => c.isActive) || list[0]
        if (active) selectConfig(active)
      }
    }).catch(console.error)
  }

  const selectConfig = async (config: RerankerConfig) => {
    setSelectedId(config.id || null)
    setIsNew(false)
    setName(config.name || '')
    setBaseUrl(config.baseUrl)
    setModel(config.model)
    setMaskedKey(config.apiKeyMasked)
    setApiKey('')
    setTestResult(null)
    // 点击列表项直接激活该配置
    if (config.id && !config.isActive) {
      try {
        await rerankerApi.switchConfig(config.id)
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
    setModel('')
    setApiKey('')
    setMaskedKey('')
    setTestResult(null)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const data: RerankerConfigRequest = {
        configId: selectedId || undefined,
        name: name || 'Reranker',
        apiKey,
        baseUrl,
        model,
      }
      const saved = await rerankerApi.updateConfig(data)
      setMaskedKey(saved.apiKeyMasked)
      setModel(saved.model)
      setApiKey('')
      setIsNew(false)
      setSelectedId(saved.id || null)
      loadConfigs()
      notify('Reranker 配置已保存', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    setTesting(true)
    try {
      const res = await rerankerApi.testConfig()
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
      await rerankerApi.deleteConfig(selectedId)
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
      await rerankerApi.switchConfig(id)
      loadConfigs()
      notify('已切换', 'success')
    } catch (err) {
      notify(err instanceof Error ? err.message : '切换失败', 'error')
    }
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">Reranker 配置</h2>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 左侧：配置列表 */}
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-white dark:bg-gray-800">
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">我的 Reranker</h3>
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
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{config.name || 'Reranker'}</span>
                    {config.isActive && (
                      <span className="text-xs px-2 py-0.5 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 rounded-full">使用中</span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{config.model}</p>
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    if (confirm(`确定删除配置「${config.name || 'Reranker'}」？`)) {
                      rerankerApi.deleteConfig(config.id!).then(() => {
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

        {/* 右侧：配置表单 */}
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
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">配置名称</label>
              <input type="text" value={name} onChange={e => setName(e.target.value)}
                placeholder="如：BGE Reranker"
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">API Base URL</label>
              <input type="text" value={baseUrl} onChange={e => setBaseUrl(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">模型名称</label>
              <input type="text" value={model} onChange={e => setModel(e.target.value)}
                placeholder="如：bge-reranker-v2-m3"
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">API Key</label>
              {maskedKey && !isNew && <p className="text-xs text-gray-400 dark:text-gray-500 mb-1">当前: {maskedKey}</p>}
              <input type="password" value={apiKey} onChange={e => setApiKey(e.target.value)}
                placeholder={isNew ? '输入 API Key' : '留空则不更新'}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
            </div>

            <div className="bg-gray-50 dark:bg-gray-700 rounded-md p-3 text-xs text-gray-500 dark:text-gray-400">
              <p className="font-medium text-gray-700 dark:text-gray-300 mb-1">Reranker 说明</p>
              <p>- Reranker 对初始检索结果进行精细重排序，提升 RAG 召回精度</p>
              <p>- 支持 OpenAI 兼容 API 及自部署模型（如 BGE Reranker）</p>
              <p>- 配置保存后，RAG 检索将自动使用激活的 Reranker</p>
            </div>

            <div className="flex gap-3">
              <button onClick={handleSave} disabled={saving}
                className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50">
                {saving ? '保存中...' : '保存'}
              </button>
              <button onClick={handleTest} disabled={testing || isNew}
                title={isNew ? '请先保存配置后再测试' : ''}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 disabled:opacity-50 disabled:cursor-not-allowed">
                {testing ? '测试中...' : '测试连接'}
              </button>
              {!isNew && selectedId && !configs.find(c => c.id === selectedId)?.isActive && (
                <button onClick={() => handleActivate(selectedId!)}
                  className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300">
                  设为默认
                </button>
              )}
            </div>

            {testResult && (
              <div className={`p-3 rounded-md text-sm ${testResult.success ? 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400' : 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                {testResult.message}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
