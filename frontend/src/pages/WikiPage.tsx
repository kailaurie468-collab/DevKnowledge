import { useState, useEffect, useCallback } from 'react'
import { wikiApi } from '@/api/wiki'
import { WikiUpload } from '@/components/wiki/WikiUpload'
import { WikiGraph3D } from '@/components/wiki/WikiGraph3D'
import type { WikiIndexEntry, WikiGraphData, WikiLintResult, WikiFailedDocument } from '@/types/wiki'

export function WikiPage() {
  const [pages, setPages] = useState<WikiIndexEntry[]>([])
  const [selectedPage, setSelectedPage] = useState<string | null>(null)
  const [pageContent, setPageContent] = useState<string>('')
  const [graphData, setGraphData] = useState<WikiGraphData | null>(null)
  const [loading, setLoading] = useState(true)
  const [graphLoading, setGraphLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'content' | 'graph'>('content')
  const [activeCategory, setActiveCategory] = useState<string>('all')
  const [analyzing, setAnalyzing] = useState(false)
  const [lintResult, setLintResult] = useState<WikiLintResult | null>(null)
  const [linting, setLinting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [failedDocs, setFailedDocs] = useState<WikiFailedDocument[]>([])

  // 加载页面列表和图谱数据
  useEffect(() => {
    loadPages()
    loadGraph()
    loadFailedDocs()
  }, [])

  const loadPages = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await wikiApi.getPages()
      setPages(data)
    } catch (err) {
      const msg = err instanceof Error ? err.message : '加载页面列表失败'
      console.error('加载页面列表失败:', err)
      setError(msg)
    } finally {
      setLoading(false)
    }
  }, [])

  const loadGraph = useCallback(async () => {
    try {
      setGraphLoading(true)
      const data = await wikiApi.getGraph()
      setGraphData(data)
    } catch (err) {
      console.error('加载图谱数据失败:', err)
    } finally {
      setGraphLoading(false)
    }
  }, [])

  // 加载页面内容
  const loadPageContent = async (path: string) => {
    try {
      setSelectedPage(path)
      setPageContent('加载中...')
      const content = await wikiApi.getPage(path)
      setPageContent(content || '（页面内容为空）')
    } catch (err) {
      const msg = err instanceof Error ? err.message : '加载失败'
      console.error('加载页面内容失败:', err)
      setPageContent(`加载失败: ${msg}`)
    }
  }

  // 加载摄取失败的文档（错误必须可见，否则用户不知道上传失败了）
  const loadFailedDocs = useCallback(async () => {
    try {
      const docs = await wikiApi.getFailedDocuments()
      setFailedDocs(docs)
    } catch (err) {
      console.error('加载失败文档列表失败:', err)
    }
  }, [])

  // 上传成功回调（接收 WikiUploadResponse 参数但仅用于触发刷新）
  const handleUploadSuccess = (_result?: unknown) => {
    loadPages()
    loadGraph()
    loadFailedDocs()
  }

  // 深度分析（也用于失败文档重试：重跑 LLM 分析）
  const handleAnalyze = async (docId: string) => {
    try {
      setAnalyzing(true)
      await wikiApi.analyzeDocument(docId)
      loadPages()
      loadGraph()
      loadFailedDocs()
    } catch (err) {
      console.error('分析失败:', err)
    } finally {
      setAnalyzing(false)
    }
  }

  // 删除失败文档
  const handleDeleteFailed = async (docId: string, filename: string) => {
    if (!confirm(`确定要删除失败文档「${filename}」吗？`)) return
    try {
      await wikiApi.deleteDocument(docId)
      loadFailedDocs()
    } catch (err) {
      console.error('删除失败:', err)
    }
  }

  // 删除文档
  const handleDelete = async (docId: string) => {
    if (!confirm('确定要删除此文档及相关内容吗？')) return
    try {
      await wikiApi.deleteDocument(docId)
      setSelectedPage(null)
      setPageContent('')
      loadPages()
      loadGraph()
    } catch (err) {
      console.error('删除失败:', err)
    }
  }

  // Lint 健康检查
  const handleLint = async () => {
    try {
      setLinting(true)
      const result = await wikiApi.lint()
      setLintResult(result)
    } catch (err) {
      console.error('Lint 失败:', err)
    } finally {
      setLinting(false)
    }
  }

  // 按分类筛选
  const filteredPages = activeCategory === 'all'
    ? pages
    : pages.filter(p => p.category === activeCategory)

  // 统计各分类数量
  const categoryCounts = {
    all: pages.length,
    entity: pages.filter(p => p.category === 'entity').length,
    concept: pages.filter(p => p.category === 'concept').length,
    source: pages.filter(p => p.category === 'source').length,
  }

  // 找到当前选中页面的 docId（用于深度分析）
  const selectedPageEntry = pages.find(p => p.pagePath === selectedPage)
  const selectedDocId = selectedPageEntry?.docIds?.[0]

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* 侧边栏 */}
      <div className="w-64 border-r border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 flex flex-col">
        {/* 上传区域 */}
        <div className="p-3 border-b border-gray-200 dark:border-gray-700">
          <WikiUpload onUploadSuccess={handleUploadSuccess} />
        </div>

        {/* 摄取失败的文档：错误原因 + 重试 / 删除 */}
        {failedDocs.length > 0 && (
          <div className="p-3 border-b border-gray-200 dark:border-gray-700 space-y-2">
            <p className="text-xs font-medium text-red-600 dark:text-red-400">
              {failedDocs.length} 个文档处理失败
            </p>
            {failedDocs.map(doc => (
              <div
                key={doc.docId}
                className="rounded-md bg-red-50 dark:bg-red-900/20 px-2 py-1.5 text-xs"
              >
                <p className="text-gray-900 dark:text-gray-100 truncate" title={doc.filename}>
                  {doc.filename}
                </p>
                <p className="text-red-600 dark:text-red-400 mt-0.5 line-clamp-2" title={doc.errorMsg}>
                  {doc.errorMsg || '未知错误'}
                </p>
                <div className="flex gap-2 mt-1">
                  <button
                    onClick={() => handleAnalyze(doc.docId)}
                    disabled={analyzing}
                    className="text-primary-600 dark:text-primary-400 hover:underline disabled:opacity-50"
                  >
                    {analyzing ? '重试中...' : '重试'}
                  </button>
                  <button
                    onClick={() => handleDeleteFailed(doc.docId, doc.filename)}
                    className="text-gray-500 hover:text-red-600 dark:hover:text-red-400"
                  >
                    删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* 分类筛选 */}
        <div className="p-2 border-b border-gray-200 dark:border-gray-700">
          <div className="flex flex-wrap gap-1">
            {(['all', 'entity', 'concept', 'source'] as const).map(cat => (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={`px-2 py-1 text-xs rounded transition-colors ${
                  activeCategory === cat
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                }`}
              >
                {cat === 'all' ? '全部' : cat === 'entity' ? '实体' : cat === 'concept' ? '概念' : '来源'}
                <span className="ml-1">({categoryCounts[cat]})</span>
              </button>
            ))}
          </div>
        </div>

        {/* 页面列表 */}
        <div className="flex-1 overflow-y-auto p-2">
          {loading ? (
            <div className="text-center text-gray-500 dark:text-gray-400 py-4">加载中...</div>
          ) : filteredPages.length === 0 ? (
            <div className="text-center text-gray-500 dark:text-gray-400 py-4">
              <p>暂无页面</p>
              <p className="text-xs mt-1">上传文档开始构建</p>
            </div>
          ) : (
            <div className="space-y-1">
              {filteredPages.map(page => (
                <button
                  key={page.id}
                  onClick={() => loadPageContent(page.pagePath)}
                  className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                    selectedPage === page.pagePath
                      ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-800 dark:text-primary-300'
                      : 'hover:bg-gray-200 dark:hover:bg-gray-700 text-gray-900 dark:text-gray-100'
                  }`}
                >
                  <div className="font-medium truncate">{page.title}</div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 truncate">{page.summary}</div>
                  <div className="flex items-center mt-1">
                    <span className={`text-xs px-1.5 py-0.5 rounded ${
                      page.category === 'entity' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' :
                      page.category === 'concept' ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' :
                      'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400'
                    }`}>
                      {page.category}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Lint 按钮 */}
        <div className="p-3 border-t border-gray-200 dark:border-gray-700">
          <button
            onClick={handleLint}
            disabled={linting || pages.length === 0}
            className="w-full px-3 py-2 text-sm bg-gray-600 dark:bg-gray-700 text-white rounded-lg hover:bg-gray-700 dark:hover:bg-gray-600 disabled:bg-gray-400 dark:disabled:bg-gray-600 transition-colors"
          >
            {linting ? '检查中...' : '🔍 健康检查'}
          </button>
        </div>
      </div>

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col">
        {/* 标签页切换 */}
        <div className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900">
          <div className="flex items-center">
            <button
              onClick={() => setActiveTab('content')}
              className={`px-6 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === 'content'
                  ? 'border-primary-600 text-primary-600 dark:text-primary-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }`}
            >
              页面内容
            </button>
            <button
              onClick={() => setActiveTab('graph')}
              className={`px-6 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === 'graph'
                  ? 'border-primary-600 text-primary-600 dark:text-primary-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }`}
            >
              知识图谱
              {graphData && graphData.entities.length > 0 && (
                <span className="ml-2 px-2 py-0.5 text-xs bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full">
                  {graphData.entities.length} 实体
                </span>
              )}
            </button>

            {/* 操作按钮 */}
            {selectedDocId && activeTab === 'content' && (
              <div className="ml-auto pr-4 flex items-center gap-2">
                <button
                  onClick={() => handleAnalyze(selectedDocId)}
                  disabled={analyzing}
                  className="px-3 py-1.5 text-sm bg-purple-600 text-white rounded hover:bg-purple-700 disabled:bg-gray-400 transition-colors"
                >
                  {analyzing ? '分析中...' : '深度分析'}
                </button>
                <button
                  onClick={() => handleDelete(selectedDocId)}
                  className="px-3 py-1.5 text-sm bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
                >
                  删除
                </button>
              </div>
            )}
          </div>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 overflow-y-auto">
          {activeTab === 'content' ? (
            <div className="p-6">
              {/* 全局错误提示 */}
              {error && (
                <div className="mb-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-center justify-between">
                  <span className="text-sm text-red-700 dark:text-red-300">{error}</span>
                  <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              )}

              {/* Lint 结果 */}
              {lintResult && (
                <div className="mb-6 bg-gray-50 dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-medium text-gray-900 dark:text-gray-100">健康检查结果</h3>
                    <button onClick={() => setLintResult(null)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
                      ✕
                    </button>
                  </div>

                  {lintResult.contradictions.length > 0 && (
                    <div className="mb-3">
                      <h4 className="text-sm font-medium text-red-600 dark:text-red-400 mb-1">⚠️ 矛盾</h4>
                      <ul className="text-sm text-gray-700 dark:text-gray-300 space-y-1">
                        {lintResult.contradictions.map((c, i) => <li key={i}>• {c}</li>)}
                      </ul>
                    </div>
                  )}

                  {lintResult.orphanPages.length > 0 && (
                    <div className="mb-3">
                      <h4 className="text-sm font-medium text-yellow-600 dark:text-yellow-400 mb-1">🔗 孤立页面</h4>
                      <ul className="text-sm text-gray-700 dark:text-gray-300 space-y-1">
                        {lintResult.orphanPages.map((p, i) => <li key={i}>• {p}</li>)}
                      </ul>
                    </div>
                  )}

                  {lintResult.suggestions.length > 0 && (
                    <div>
                      <h4 className="text-sm font-medium text-blue-600 dark:text-blue-400 mb-1">💡 建议</h4>
                      <ul className="text-sm text-gray-700 dark:text-gray-300 space-y-1">
                        {lintResult.suggestions.map((s, i) => <li key={i}>• {s}</li>)}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* 页面内容 */}
              {selectedPage ? (
                <div className="prose max-w-none">
                  <pre className="whitespace-pre-wrap font-sans text-sm leading-relaxed text-gray-900 dark:text-gray-100">{pageContent}</pre>
                </div>
              ) : (
                <div className="flex items-center justify-center h-96 text-gray-500 dark:text-gray-400">
                  <div className="text-center">
                    <svg className="w-16 h-16 mx-auto mb-4 text-gray-300 dark:text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    <p>选择左侧页面查看内容</p>
                    <p className="text-sm mt-2">或上传文档开始构建知识图谱</p>
                  </div>
                </div>
              )}
            </div>
          ) : (
            // 图谱标签页
            graphLoading ? (
              <div className="flex items-center justify-center h-full text-gray-500 dark:text-gray-400">
                <div className="text-center">
                  <svg className="animate-spin w-12 h-12 mx-auto mb-4 text-gray-400" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  <p>加载图谱数据中...</p>
                </div>
              </div>
            ) : graphData && graphData.entities.length > 0 ? (
              <WikiGraph3D
                data={graphData}
                onNodeClick={(_entityId, pagePath) => {
                  if (pagePath) {
                    loadPageContent(pagePath)
                    setActiveTab('content')
                  }
                }}
              />
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500 dark:text-gray-400">
                <div className="text-center">
                  <svg className="w-16 h-16 mx-auto mb-4 text-gray-300 dark:text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
                  </svg>
                  <p>暂无图谱数据</p>
                  <p className="text-sm mt-2">上传文档并进行深度分析</p>
                </div>
              </div>
            )
          )}
        </div>
      </div>
    </div>
  )
}
