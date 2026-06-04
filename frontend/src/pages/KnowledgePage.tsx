import { useState, useEffect, useCallback } from 'react'
import { knowledgeApi, type WebSearchResult } from '@/api/knowledge'
import { SearchBar } from '@/components/knowledge/SearchBar'
import { FrameworkGrid } from '@/components/knowledge/FrameworkGrid'
import { LinkCard } from '@/components/knowledge/LinkCard'
import type { Framework, KnowledgeLink, LinkSearchResult } from '@/types/api'

export function KnowledgePage() {
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [selectedFw, setSelectedFw] = useState<Framework | null>(null)
  const [links, setLinks] = useState<KnowledgeLink[]>([])
  const [searchResults, setSearchResults] = useState<LinkSearchResult[]>([])
  const [webResults, setWebResults] = useState<WebSearchResult[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  // 框架内搜索
  const [fwSearchQuery, setFwSearchQuery] = useState('')
  const [fwSearchResults, setFwSearchResults] = useState<LinkSearchResult[]>([])
  const [fwSearching, setFwSearching] = useState(false)

  useEffect(() => {
    knowledgeApi.getFrameworks().then(setFrameworks).catch(console.error)
  }, [])

  useEffect(() => {
    if (selectedFw) {
      knowledgeApi.getFrameworkLinks(selectedFw.slug).then(setLinks).catch(console.error)
      // 切换框架时清除框架内搜索
      setFwSearchQuery('')
      setFwSearchResults([])
    }
  }, [selectedFw])

  // 全局搜索
  const handleSearch = useCallback(async (query: string) => {
    if (!query.trim()) {
      setSearchResults([])
      setWebResults([])
      setIsSearching(false)
      setSearchQuery('')
      return
    }
    setSearchQuery(query)
    setIsSearching(true)
    try {
      const [localResults, webSearchResults] = await Promise.all([
        knowledgeApi.searchLinks(query).catch(() => []),
        knowledgeApi.webSearch(query, 8).catch(() => []),
      ])
      setSearchResults(localResults)
      setWebResults(webSearchResults)
    } catch (err) {
      console.error(err)
    } finally {
      setIsSearching(false)
    }
  }, [])

  // 框架内搜索
  const handleFwSearch = useCallback(async (query: string) => {
    if (!query.trim() || !selectedFw) {
      setFwSearchQuery('')
      setFwSearchResults([])
      return
    }
    setFwSearchQuery(query)
    setFwSearching(true)
    try {
      const results = await knowledgeApi.searchLinks(query, selectedFw.slug).catch(() => [])
      setFwSearchResults(results)
    } catch (err) {
      console.error(err)
    } finally {
      setFwSearching(false)
    }
  }, [selectedFw])

  // 框架内搜索 → 跳转到全局搜索
  const expandToGlobalSearch = useCallback(() => {
    if (fwSearchQuery) {
      setSearchQuery(fwSearchQuery)
      handleSearch(fwSearchQuery)
      setFwSearchQuery('')
      setFwSearchResults([])
    }
  }, [fwSearchQuery, handleSearch])

  const showGlobalSearch = searchQuery.length > 0

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">知识搜索</h1>

      <div className="mb-6">
        <SearchBar onSearch={handleSearch} placeholder="搜索文档... 例如 React useEffect" />
      </div>

      {showGlobalSearch ? (
        <div className="space-y-8">
          <button
            onClick={() => { setSearchQuery(''); setSearchResults([]); setWebResults([]) }}
            className="text-sm text-primary-600 dark:text-primary-400 hover:underline"
          >
            ← 返回框架列表
          </button>

          {/* 本地知识库结果 */}
          <div>
            <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">
              {isSearching ? '搜索中...' : `本地知识库 · ${searchResults.length} 条结果`}
            </h2>
            {searchResults.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {searchResults.map(result => (
                  <LinkCard key={result.link.id} result={result} />
                ))}
              </div>
            ) : (
              !isSearching && <p className="text-sm text-gray-400 dark:text-gray-500">无匹配结果</p>
            )}
          </div>

          {/* Web 搜索结果 */}
          <div>
            <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">
              {isSearching ? '' : `Web 搜索 · ${webResults.length} 条结果`}
            </h2>
            {webResults.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {webResults.map((result, i) => (
                  <a
                    key={i}
                    href={result.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block p-4 border border-gray-200 dark:border-gray-700 rounded-lg hover:border-primary-300 dark:hover:border-primary-500 hover:shadow-sm transition-all bg-white dark:bg-gray-800"
                  >
                    <h3 className="font-medium text-gray-900 dark:text-gray-100 text-sm mb-1">{result.title}</h3>
                    <p className="text-xs text-gray-400 dark:text-gray-500 mb-1.5 truncate">{result.url}</p>
                    {result.description && (
                      <p className="text-sm text-gray-500 dark:text-gray-400 line-clamp-2">{result.description}</p>
                    )}
                  </a>
                ))}
              </div>
            ) : (
              !isSearching && <p className="text-sm text-gray-400">无 Web 结果</p>
            )}
          </div>
        </div>
      ) : selectedFw ? (
        <div>
          <button
            onClick={() => { setSelectedFw(null); setLinks([]) }}
            className="text-sm text-primary-600 dark:text-primary-400 hover:underline mb-4"
          >
            ← 返回框架列表
          </button>
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-3">{selectedFw.name}</h2>

          {/* 框架内搜索框 */}
          <div className="mb-4">
            <SearchBar onSearch={handleFwSearch} placeholder={`在 ${selectedFw.name} 中搜索...`} />
          </div>

          {/* 框架内搜索结果 */}
          {fwSearchQuery ? (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400">
                  {fwSearching ? '搜索中...' : `${selectedFw.name} · ${fwSearchResults.length} 条结果`}
                </h3>
                <button
                  onClick={() => { setFwSearchQuery(''); setFwSearchResults([]) }}
                  className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                >
                  清除搜索
                </button>
              </div>
              {fwSearchResults.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {fwSearchResults.map(result => (
                    <LinkCard key={result.link.id} result={result} />
                  ))}
                </div>
              ) : (
                !fwSearching && <p className="text-sm text-gray-400">当前框架无匹配结果</p>
              )}
              <button
                onClick={expandToGlobalSearch}
                className="mt-4 text-sm text-primary-600 hover:underline"
              >
                搜索全部框架 →
              </button>
            </div>
          ) : (
            /* 框架链接列表 */
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {links.map(link => (
                <a
                  key={link.id}
                  href={link.anchor ? `${link.url}#${link.anchor}` : link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block p-3 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
                >
                  <h3 className="font-medium text-sm text-gray-900 mb-1">{link.title}</h3>
                  {link.description && (
                    <p className="text-xs text-gray-500 line-clamp-2">{link.description}</p>
                  )}
                  {link.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {link.tags.map(tag => (
                        <span key={tag} className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded">
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </a>
              ))}
              {links.length === 0 && <p className="text-gray-500 text-sm">暂无链接。</p>}
            </div>
          )}
        </div>
      ) : (
        <div>
          <h2 className="text-sm font-medium text-gray-500 mb-3">框架列表</h2>
          <FrameworkGrid frameworks={frameworks} onSelect={setSelectedFw} />
        </div>
      )}
    </div>
  )
}
