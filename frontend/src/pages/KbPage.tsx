import { useState, useEffect, useCallback } from 'react'
import { kbApi } from '@/api/kb'
import { useAuthStore } from '@/stores/authStore'
import { SearchBar } from '@/components/knowledge/SearchBar'
import type { KnowledgeBase, KbDocument, KbChunk } from '@/types/api'

export function KbPage() {
  const { isAuthenticated } = useAuthStore()
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null)
  const [documents, setDocuments] = useState<KbDocument[]>([])
  const [searchResults, setSearchResults] = useState<KbChunk[]>([])
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [showCreate, setShowCreate] = useState(false)

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

  const handleCreate = async () => {
    if (!newName.trim()) return
    try {
      await kbApi.createKb({ name: newName, description: newDesc || undefined })
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
      <h1 className="text-2xl font-bold text-gray-900 mb-4">知识库</h1>

      {showCreate ? (
        <div className="mb-6 p-4 border border-gray-200 rounded-lg space-y-3">
          <input
            type="text"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            placeholder="知识库名称"
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
          <input
            type="text"
            value={newDesc}
            onChange={e => setNewDesc(e.target.value)}
            placeholder="描述（可选）"
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
          <div className="flex gap-2">
            <button onClick={handleCreate} className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm">创建</button>
            <button onClick={() => setShowCreate(false)} className="px-3 py-1.5 border border-gray-300 rounded-md text-sm">取消</button>
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
          <button
            onClick={() => { setSelectedKb(null); setDocuments([]); setSearchResults([]) }}
            className="text-sm text-primary-600 hover:underline mb-4"
          >
            返回列表
          </button>
          <h2 className="text-lg font-semibold mb-3">{selectedKb.name}</h2>

          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">上传文档（MD/TXT）</label>
            <input type="file" accept=".md,.txt" onChange={handleUpload} className="text-sm" />
          </div>

          {documents.length > 0 && (
            <div className="mb-6">
              <h3 className="text-sm font-medium text-gray-500 mb-2">文档列表（{documents.length}）</h3>
              <div className="space-y-2">
                {documents.map(doc => (
                  <div key={doc.id} className="flex items-center justify-between p-2 border border-gray-200 rounded">
                    <div>
                      <span className="text-sm font-medium">{doc.filename}</span>
                      <span className="text-xs text-gray-400 ml-2">{doc.chunkCount} 个分块</span>
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
            </div>
          )}

          <div className="mb-4">
            <h3 className="text-sm font-medium text-gray-500 mb-2">语义搜索</h3>
            <SearchBar onSearch={handleSearch} placeholder="在此知识库中搜索..." />
          </div>

          {searchResults.length > 0 && (
            <div className="space-y-2">
              {searchResults.map(chunk => (
                <div key={chunk.id} className="p-3 bg-gray-50 rounded border border-gray-200">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-xs text-gray-400">相似度: {(chunk.similarity * 100).toFixed(1)}%</span>
                  </div>
                  <p className="text-sm text-gray-700 whitespace-pre-wrap">{chunk.content}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          {kbs.map(kb => (
            <div key={kb.id} className="flex items-center justify-between p-3 border border-gray-200 rounded-lg">
              <button onClick={() => setSelectedKb(kb)} className="text-left flex-1">
                <h3 className="font-medium text-sm text-gray-900">{kb.name}</h3>
                {kb.description && <p className="text-xs text-gray-500">{kb.description}</p>}
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
