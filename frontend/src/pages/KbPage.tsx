import { useState, useEffect, useCallback, useRef } from 'react'
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, useSortable, verticalListSortingStrategy, arrayMove } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { kbApi } from '@/api/kb'
import { embeddingApi } from '@/api/embedding'
import { useAuthStore } from '@/stores/authStore'
import { useNotify } from '@/stores/notify'
import { SearchBar } from '@/components/knowledge/SearchBar'
import type { KnowledgeBase, KbDocument, EmbeddingConfig } from '@/types/api'

/** 可排序的知识库列表项 */
function SortableKbItem({ kb, onSelect, onDelete }: {
  kb: KnowledgeBase
  onSelect: () => void
  onDelete: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: kb.id })
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <div ref={setNodeRef} style={style}
      className="flex items-center p-3 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800">
      {/* 拖拽手柄 — 用 div 避免 button 捕获鼠标事件 */}
      <div {...attributes} {...listeners}
        className="mr-3 cursor-grab active:cursor-grabbing text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 shrink-0 touch-none">
        <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
          <circle cx="9" cy="5" r="1.5" /><circle cx="15" cy="5" r="1.5" />
          <circle cx="9" cy="10" r="1.5" /><circle cx="15" cy="10" r="1.5" />
          <circle cx="9" cy="15" r="1.5" /><circle cx="15" cy="15" r="1.5" />
          <circle cx="9" cy="20" r="1.5" /><circle cx="15" cy="20" r="1.5" />
        </svg>
      </div>
      <div onClick={onSelect} className="text-left flex-1 min-w-0 cursor-pointer">
        <h3 className="font-medium text-sm text-gray-900 dark:text-gray-100">{kb.name}</h3>
        {kb.description && <p className="text-xs text-gray-500 dark:text-gray-400 truncate">{kb.description}</p>}
        {kb.embeddingModel && (
          <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">模型: {kb.embeddingModel}</p>
        )}
      </div>
      <button onClick={onDelete}
        className="text-xs text-red-500 hover:underline ml-4 shrink-0">
        删除
      </button>
    </div>
  )
}

export function KbPage() {
  const { isAuthenticated } = useAuthStore()
  const { notify } = useNotify()
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null)
  const [documents, setDocuments] = useState<KbDocument[]>([])
  const [searchResults, setSearchResults] = useState<KbDocument[]>([])
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [showEmbeddingConfig, setShowEmbeddingConfig] = useState(false)
  const [embeddingConfigs, setEmbeddingConfigs] = useState<EmbeddingConfig[]>([])
  const [hasEmbeddingConfig, setHasEmbeddingConfig] = useState(false)
  const [selectedEmbedId, setSelectedEmbedId] = useState<string>('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Embedding 配置表单
  const [embedName, setEmbedName] = useState('')
  const [embedBaseUrl, setEmbedBaseUrl] = useState('https://api.openai.com/v1')
  const [embedApiKey, setEmbedApiKey] = useState('')
  const [embedModel, setEmbedModel] = useState('text-embedding-3-small')
  const [embedTesting, setEmbedTesting] = useState(false)
  const [embedTestResult, setEmbedTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [embedSaving, setEmbedSaving] = useState(false)

  const loadKbs = useCallback(() => {
    kbApi.getKbs().then(setKbs).catch(console.error)
  }, [])

  const loadEmbeddingConfigs = useCallback(() => {
    embeddingApi.getAllConfigs().then(list => {
      setEmbeddingConfigs(list)
      setHasEmbeddingConfig(list.length > 0)
    }).catch(console.error)
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      loadKbs()
      loadEmbeddingConfigs()
    }
  }, [isAuthenticated, loadKbs, loadEmbeddingConfigs])

  useEffect(() => {
    if (selectedKb) {
      kbApi.getDocuments(selectedKb.id).then(setDocuments).catch(console.error)
    }
  }, [selectedKb])

  // 轮询：当有文档处于 processing/embedding 状态时，每 3 秒刷新一次
  useEffect(() => {
    if (!selectedKb) return
    const hasPending = documents.some(d => d.status === 'processing' || d.status === 'embedding')
    if (!hasPending) return

    const timer = setInterval(() => {
      kbApi.getDocuments(selectedKb.id).then(latest => {
        setDocuments(latest)
        const stillPending = latest.some(d => d.status === 'processing' || d.status === 'embedding')
        if (!stillPending) clearInterval(timer)
      }).catch(() => clearInterval(timer))
    }, 3000)

    return () => clearInterval(timer)
  }, [selectedKb, documents])

  // 点击"新建知识库"按钮
  const handleClickCreate = () => {
    // 默认选中 active 配置
    const active = embeddingConfigs.find(c => c.isActive)
    if (active?.id) setSelectedEmbedId(active.id)
    setShowCreate(true)
  }

  // 选中 Embedding 配置
  const selectConfig = (config: EmbeddingConfig) => {
    if (config.id) setSelectedEmbedId(config.id)
  }

  // 测试 Embedding 连接
  const handleEmbedTest = async () => {
    setEmbedTesting(true)
    setEmbedTestResult(null)
    try {
      const res = await embeddingApi.testConfig()
      setEmbedTestResult(res)
    } catch (err) {
      setEmbedTestResult({ success: false, message: err instanceof Error ? err.message : '测试失败' })
    } finally {
      setEmbedTesting(false)
    }
  }

  // 保存 Embedding 配置
  const handleEmbedSave = async () => {
    // 验证必填项
    if (!embedName.trim()) { notify('请输入配置名称', 'error'); return }
    if (!embedModel.trim()) { notify('请输入 Embedding 模型名', 'error'); return }
    if (!embedBaseUrl.trim()) { notify('请输入 API Base URL', 'error'); return }
    if (!embedApiKey.trim()) { notify('请输入 API Key', 'error'); return }

    setEmbedSaving(true)
    try {
      await embeddingApi.updateConfig({
        name: embedName.trim(),
        apiKey: embedApiKey,
        baseUrl: embedBaseUrl.trim(),
        modelName: embedModel.trim(),
      })
      notify('Embedding 配置已保存', 'success')
      setShowEmbeddingConfig(false)
      loadEmbeddingConfigs()
      // 配置创建好后，显示 KB 创建表单
      setShowCreate(true)
    } catch (err) {
      notify(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setEmbedSaving(false)
    }
  }

  const handleCreate = async () => {
    if (!newName.trim()) return
    try {
      await kbApi.createKb({
        name: newName,
        description: newDesc || undefined,
      })
      setNewName('')
      setNewDesc('')
      setShowCreate(false)
      loadKbs()
    } catch (err) {
      console.error(err)
    }
  }

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!selectedKb || !e.target.files?.length) return
    const files = Array.from(e.target.files).slice(0, 3) // 最多 3 个
    try {
      if (files.length === 1) {
        await kbApi.uploadDocument(selectedKb.id, files[0])
      } else {
        await kbApi.batchUpload(selectedKb.id, files)
      }
      notify(`已上传 ${files.length} 个文档`, 'success')
      kbApi.getDocuments(selectedKb.id).then(setDocuments).catch(console.error)
      loadKbs()
    } catch (err) {
      notify(err instanceof Error ? err.message : '上传失败', 'error')
    }
    e.target.value = ''
  }

  const handleSearch = useCallback(async (query: string) => {
    if (!selectedKb || !query.trim()) {
      setSearchResults([])
      return
    }
    try {
      const results = await kbApi.searchKb(selectedKb.id, query)
      setSearchResults(results)
    } catch (err) {
      console.error(err)
    }
  }, [selectedKb])

  const handleDelete = async (id: string) => {
    try {
      await kbApi.deleteKb(id)
      if (selectedKb?.id === id) setSelectedKb(null)
      loadKbs()
    } catch (err) {
      console.error(err)
    }
  }

  // 拖拽排序传感器，设置 8px 拖拽阈值避免误触
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }))

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return

    const oldIndex = kbs.findIndex(k => k.id === active.id)
    const newIndex = kbs.findIndex(k => k.id === over.id)
    if (oldIndex === -1 || newIndex === -1) return

    // 乐观更新本地状态
    const reordered = arrayMove(kbs, oldIndex, newIndex)
    setKbs(reordered)

    // 持久化到后端
    try {
      await kbApi.reorderKbs(reordered.map(k => k.id))
    } catch (err) {
      console.error('排序保存失败:', err)
      notify('排序保存失败', 'error')
      loadKbs() // 回滚
    }
  }

  // 重试文档解析
  const handleRetry = async (docId: string) => {
    try {
      await kbApi.retryDocument(docId)
      notify('正在重新解析文档...', 'success')
      // 刷新文档列表
      if (selectedKb) {
        kbApi.getDocuments(selectedKb.id).then(setDocuments).catch(console.error)
      }
    } catch (err) {
      notify('重试失败，请稍后再试', 'error')
      console.error(err)
    }
  }

  // 获取当前选中的 Embedding 配置
  const activeEmbedConfig = embeddingConfigs.find(c => c.id === selectedEmbedId) || embeddingConfigs.find(c => c.isActive)

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">知识库</h1>

      {/* Embedding 配置弹窗 */}
      {showEmbeddingConfig && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-md mx-4">
            <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100 mb-4">配置 Embedding AI</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              创建知识库前，需要先配置 Embedding AI 服务。配置完成后可在设置页查看。
            </p>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">配置名称</label>
                <input type="text" value={embedName} onChange={e => setEmbedName(e.target.value)}
                  placeholder="My Embedding AI"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Embedding 模型</label>
                <input type="text" value={embedModel} onChange={e => setEmbedModel(e.target.value)}
                  placeholder="如：text-embedding-3-small"
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">常见模型：text-embedding-3-small、text-embedding-3-large、text-embedding-ada-002</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">API Base URL</label>
                <input type="text" value={embedBaseUrl} onChange={e => setEmbedBaseUrl(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">API Key</label>
                <input type="password" value={embedApiKey} onChange={e => setEmbedApiKey(e.target.value)}
                  placeholder="sk-..."
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100" />
              </div>

              {embedTestResult && (
                <div className={`p-3 rounded-md text-sm ${embedTestResult.success ? 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400' : 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                  {embedTestResult.message}
                </div>
              )}

              <div className="flex gap-2">
                <button onClick={handleEmbedSave} disabled={embedSaving}
                  className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50">
                  {embedSaving ? '保存中...' : '保存并继续'}
                </button>
                <button onClick={handleEmbedTest} disabled={embedTesting}
                  className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 disabled:opacity-50">
                  {embedTesting ? '测试中...' : '测试连接'}
                </button>
                <button onClick={() => setShowEmbeddingConfig(false)}
                  className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700">
                  取消
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* KB 创建表单 */}
      {showCreate ? (
        <div className="mb-6 p-4 border border-gray-200 dark:border-gray-700 rounded-lg space-y-3 bg-white dark:bg-gray-800">
          {/* Embedding 配置选择 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Embedding 配置</label>
            {embeddingConfigs.length > 0 ? (
              <div className="space-y-2">
                <select
                  value={selectedEmbedId}
                  onChange={e => {
                    const config = embeddingConfigs.find(c => c.id === e.target.value)
                    if (config) selectConfig(config)
                  }}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                >
                  {embeddingConfigs.map(config => (
                    <option key={config.id} value={config.id}>
                      {config.name || 'Embedding AI'} - {config.modelName || '未指定模型'} {config.isActive ? '(使用中)' : ''}
                    </option>
                  ))}
                </select>
                <button
                  onClick={() => setShowEmbeddingConfig(true)}
                  className="text-xs text-primary-600 dark:text-primary-400 hover:underline"
                >
                  + 新建 Embedding 配置
                </button>
              </div>
            ) : (
              <div className="bg-amber-50 dark:bg-amber-900/20 rounded-md p-3">
                <p className="text-sm text-amber-700 dark:text-amber-300 mb-2">尚未配置 Embedding AI</p>
                <button
                  onClick={() => setShowEmbeddingConfig(true)}
                  className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700"
                >
                  配置 Embedding AI
                </button>
              </div>
            )}
          </div>

          {/* 选中配置的详情 */}
          {selectedEmbedId && activeEmbedConfig && (
            <div className="bg-blue-50 dark:bg-blue-900/20 rounded-md p-3 text-xs text-blue-700 dark:text-blue-300">
              <p className="font-medium">使用配置: {activeEmbedConfig.name}</p>
              <p>模型: {activeEmbedConfig.modelName || '未指定'} | {activeEmbedConfig.baseUrl}</p>
            </div>
          )}

          <input
            type="text"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            placeholder="知识库名称"
            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
          />
          <input
            type="text"
            value={newDesc}
            onChange={e => setNewDesc(e.target.value)}
            placeholder="描述（可选）"
            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
          />
          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={!selectedEmbedId} className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm disabled:opacity-50 disabled:cursor-not-allowed">创建</button>
            <button onClick={() => setShowCreate(false)} className="px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md text-sm text-gray-700 dark:text-gray-300">← 返回</button>
          </div>
        </div>
      ) : (
        <button
          onClick={handleClickCreate}
          className="mb-6 px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700"
        >
          新建知识库
        </button>
      )}

      {selectedKb ? (
        <div>
          <div className="flex items-center justify-between mb-4">
            <button
              onClick={() => { setSelectedKb(null); setDocuments([]); setSearchResults([]) }}
              className="text-sm text-primary-600 dark:text-primary-400 hover:underline"
            >
              ← 返回列表
            </button>
          </div>

          <div className="flex items-center justify-between mb-1">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">{selectedKb.name}</h2>
            <div className="flex gap-2">
              <input
                ref={fileInputRef}
                type="file"
                accept=".txt,.md,.markdown,.pdf,.docx"
                multiple
                onChange={handleUpload}
                className="hidden"
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 transition-colors"
              >
                + 上传文档（最多3个）
              </button>
            </div>
          </div>
          <p className="text-xs text-gray-400 dark:text-gray-500 mb-4">支持 TXT / MD / PDF / DOCX，单文件 ≤ 10MB，每次最多 3 个，每库 ≤ 200 个文档</p>

          {/* 搜索栏 */}
          <div className="mb-4">
            <SearchBar onSearch={handleSearch} placeholder="在知识库中搜索文档..." />
          </div>

          {/* 搜索结果 */}
          {searchResults.length > 0 && (
            <div className="mb-6 space-y-2">
              <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">搜索结果（{searchResults.length}）</h3>
              {searchResults.map(doc => (
                <div key={doc.id} className="p-3 bg-gray-50 dark:bg-gray-800 rounded border border-gray-200 dark:border-gray-700">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-xs font-medium text-gray-600 dark:text-gray-400">{doc.filename}</span>
                    <span className="text-xs text-gray-400 dark:text-gray-500">{doc.fileType}</span>
                  </div>
                  <p className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap line-clamp-5">
                    {doc.content?.slice(0, 500)}
                  </p>
                </div>
              ))}
            </div>
          )}

          {/* 未向量化全局提示 */}
          {!hasEmbeddingConfig && documents.some(d => d.status === 'ready' && (!d.chunkCount || d.chunkCount === 0)) && (
            <div className="mb-4 p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg">
              <div className="flex items-start gap-2">
                <svg className="w-4 h-4 text-amber-500 mt-0.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
                </svg>
                <div>
                  <p className="text-sm font-medium text-amber-800 dark:text-amber-300">部分文档未向量化</p>
                  <p className="text-xs text-amber-600 dark:text-amber-400 mt-1">
                    未配置 Embedding AI，文档仅支持基础关键词搜索。配置 Embedding 后可获得更精准的语义检索效果，提升 RAG 检索质量。
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* 文档列表 */}
          <div>
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">文档列表（{documents.length}）</h3>
            {documents.length > 0 ? (
              <div className="space-y-2">
                {documents.map(doc => (
                  <div key={doc.id} className="flex items-center justify-between p-2 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
                    <div>
                      <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{doc.filename}</span>
                      <span className={`text-xs ml-2 px-1.5 py-0.5 rounded ${
                        doc.status === 'ready' && doc.chunkCount != null && doc.chunkCount > 0
                          ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
                          : doc.status === 'ready'
                            ? 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400'
                            : doc.status === 'error' ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400' :
                        doc.status === 'embedding' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' :
                        'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
                      }`}>
                        {doc.status === 'ready' && doc.chunkCount != null && doc.chunkCount > 0
                          ? '已向量化'
                          : doc.status === 'ready' ? '未向量化' :
                        doc.status === 'error' ? '错误' : doc.status === 'embedding' ? '向量化中' : '解析中'}
                      </span>
                      {doc.chunkCount != null && doc.chunkCount > 0 && (
                        <span className="text-xs text-gray-400 dark:text-gray-500 ml-2">{doc.chunkCount} chunks</span>
                      )}
                      <span className="text-xs text-gray-400 ml-2">{doc.fileType}</span>
                      {doc.status === 'error' && doc.errorMessage && (
                        <p className="text-xs text-red-500 dark:text-red-400 mt-1 truncate max-w-md" title={doc.errorMessage}>
                          {doc.errorMessage}
                        </p>
                      )}
                      {doc.warningMessage && (
                        <p className="text-xs text-amber-600 dark:text-amber-400 mt-1">
                          {doc.warningMessage}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {doc.status === 'error' && (
                        <button
                          onClick={() => handleRetry(doc.id)}
                          className="text-xs text-amber-600 dark:text-amber-400 hover:underline"
                        >
                          重试
                        </button>
                      )}
                      <button
                        onClick={() => kbApi.deleteDocument(doc.id).then(() => kbApi.getDocuments(selectedKb.id).then(setDocuments))}
                        className="text-xs text-red-500 hover:underline"
                      >
                        删除
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-400 text-sm">暂无文档，点击右上角「上传文档」添加</p>
            )}
          </div>
        </div>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={kbs.map(k => k.id)} strategy={verticalListSortingStrategy}>
            <div className="space-y-2">
              {kbs.map(kb => (
                <SortableKbItem
                  key={kb.id}
                  kb={kb}
                  onSelect={() => setSelectedKb(kb)}
                  onDelete={() => handleDelete(kb.id)}
                />
              ))}
              {kbs.length === 0 && <p className="text-gray-500 dark:text-gray-400 text-sm">暂无知识库。</p>}
            </div>
          </SortableContext>
        </DndContext>
      )}
    </div>
  )
}
