import { useState, useEffect, useCallback, useRef } from 'react'
import { kbApi } from '@/api/kb'
import { useAuthStore } from '@/stores/authStore'
import { useNotify } from '@/stores/notify'
import { SearchBar } from '@/components/knowledge/SearchBar'
import type { KnowledgeBase, KbDocument } from '@/types/api'

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
  const [embeddingModel, setEmbeddingModel] = useState('text-embedding-3-small')
  const [embeddingDimensions, setEmbeddingDimensions] = useState<number | undefined>(undefined)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const loadKbs = useCallback(() => {
    kbApi.getKbs().then(setKbs).catch(console.error)
  }, [])

  useEffect(() => {
    if (isAuthenticated) loadKbs()
  }, [isAuthenticated, loadKbs])

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

  const handleCreate = async () => {
    if (!newName.trim()) return
    try {
      if (embeddingModel === 'text-embedding-3-large' && embeddingDimensions !== 1536) {
        notify('large 模型必须设置 dimensions=1536', 'error')
        return
      }
      await kbApi.createKb({
        name: newName,
        description: newDesc || undefined,
        embeddingModel,
        embeddingDimensions,
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
    if (!selectedKb || !e.target.files?.[0]) return
    try {
      await kbApi.uploadDocument(selectedKb.id, e.target.files[0])
      kbApi.getDocuments(selectedKb.id).then(setDocuments).catch(console.error)
      loadKbs()
    } catch (err) {
      console.error(err)
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

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">知识库</h1>

      {showCreate ? (
        <div className="mb-6 p-4 border border-gray-200 dark:border-gray-700 rounded-lg space-y-3 bg-white dark:bg-gray-800">
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
            {/* Embedding 模型选择 */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Embedding 模型</label>
              <select
                value={embeddingModel}
                onChange={e => {
                  setEmbeddingModel(e.target.value)
                  if (e.target.value === 'text-embedding-ada-002') setEmbeddingDimensions(undefined)
                }}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
              >
                <option value="text-embedding-3-small">text-embedding-3-small（推荐，成本最低）</option>
                <option value="text-embedding-3-large">text-embedding-3-large（效果最好）</option>
                <option value="text-embedding-ada-002">text-embedding-ada-002（上一代）</option>
              </select>
            </div>

            {/* 向量维度 */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                向量维度
                <span className="text-xs text-gray-400 dark:text-gray-500 ml-2">（可选，创建后不可更改）</span>
              </label>
              <input
                type="number"
                value={embeddingDimensions || ''}
                onChange={e => setEmbeddingDimensions(e.target.value ? Number(e.target.value) : undefined)}
                placeholder={embeddingModel === 'text-embedding-3-small' ? '推荐 512，留空=1536' :
                             embeddingModel === 'text-embedding-3-large' ? '必须填 1536' :
                             'ada-002 不支持 dimensions'}
                disabled={embeddingModel === 'text-embedding-ada-002'}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 disabled:bg-gray-100 dark:disabled:bg-gray-600 disabled:text-gray-400"
              />
              {embeddingModel === 'text-embedding-3-large' && (
                <p className="text-xs text-amber-600 dark:text-amber-400 mt-1">large 模型必须设置 dimensions=1536</p>
              )}
            </div>
          <div className="flex gap-2">
            <button onClick={handleCreate} className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm">创建</button>
            <button onClick={() => setShowCreate(false)} className="px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md text-sm text-gray-700 dark:text-gray-300">← 返回</button>
          </div>
        </div>
      ) : (
        <button
          onClick={() => setShowCreate(true)}
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
                onChange={handleUpload}
                className="hidden"
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 transition-colors"
              >
                + 上传文档
              </button>
            </div>
          </div>
          <p className="text-xs text-gray-400 dark:text-gray-500 mb-4">支持 TXT / MD / PDF / DOCX，单文件 ≤ 10MB，每库 ≤ 200 个文档</p>

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
                        doc.status === 'ready' ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' :
                        doc.status === 'error' ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400' :
                        doc.status === 'embedding' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' :
                        'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
                      }`}>
                        {doc.status === 'ready' ? '就绪' : doc.status === 'error' ? '错误' : doc.status === 'embedding' ? '向量化中' : '解析中'}
                      </span>
                      {doc.chunkCount != null && doc.chunkCount > 0 && (
                        <span className="text-xs text-gray-400 dark:text-gray-500 ml-2">{doc.chunkCount} chunks</span>
                      )}
                      <span className="text-xs text-gray-400 ml-2">{doc.fileType}</span>
                    </div>
                    <button
                      onClick={() => kbApi.deleteDocument(doc.id).then(() => kbApi.getDocuments(selectedKb.id).then(setDocuments))}
                      className="text-xs text-red-500 hover:underline"
                    >
                      删除
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-400 text-sm">暂无文档，点击右上角「上传文档」添加</p>
            )}
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          {kbs.map(kb => (
            <div key={kb.id} className="flex items-center justify-between p-3 border border-gray-200 rounded-lg">
              <button onClick={() => setSelectedKb(kb)} className="text-left flex-1">
                <h3 className="font-medium text-sm text-gray-900">{kb.name}</h3>
                {kb.description && <p className="text-xs text-gray-500">{kb.description}</p>}
                {kb.embeddingModel && (
                  <p className="text-xs text-gray-400 mt-1">
                    模型: {kb.embeddingModel}
                    {kb.embeddingDimensions ? ` | 维度: ${kb.embeddingDimensions}` : ''}
                  </p>
                )}
              </button>
              <button
                onClick={() => handleDelete(kb.id)}
                className="text-xs text-red-500 hover:underline ml-4"
              >
                删除
              </button>
            </div>
          ))}
          {kbs.length === 0 && <p className="text-gray-500 text-sm">暂无知识库。</p>}
        </div>
      )}
    </div>
  )
}
