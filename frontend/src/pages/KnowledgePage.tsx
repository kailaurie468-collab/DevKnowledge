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

  useEffect(() => {
    knowledgeApi.getFrameworks().then(setFrameworks).catch(console.error)
  }, [])

  useEffect(() => {
    if (selectedFw) {
      knowledgeApi.getFrameworkLinks(selectedFw.slug).then(setLinks).catch(console.error)
    }
  }, [selectedFw])

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
      // 同时搜索本地知识库和 Web
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

  const showSearch = searchQuery.length > 0

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-4">知识搜索</h1>

      <div className="mb-6">
        <SearchBar onSearch={handleSearch} placeholder="搜索文档... 例如 React useEffect" />
      </div>

      {showSearch ? (
        <div className="space-y-8">
          <button
            onClick={() => { setSearchQuery(''); setSearchResults([]); setWebResults([]) }}
            className="text-sm text-primary-600 hover:underline"
          >
            ← 返回框架列表
          </button>

          {/* 本地知识库结果 */}
          <div>
            <h2 className="text-sm font-medium text-gray-500 mb-3">
              {isSearching ? '搜索中...' : `本地知识库 · ${searchResults.length} 条结果`}
            </h2>
            {searchResults.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {searchResults.map(result => (
                  <LinkCard key={result.link.id} result={result} />
                ))}
              </div>
            ) : (
              !isSearching && <p className="text-sm text-gray-400">无匹配结果</p>
            )}
          </div>

          {/* Web 搜索结果 */}
          <div>
            <h2 className="text-sm font-medium text-gray-500 mb-3">
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
                    className="block p-4 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
                  >
                    <h3 className="font-medium text-gray-900 text-sm mb-1">{result.title}</h3>
                    <p className="text-xs text-gray-400 mb-1.5 truncate">{result.url}</p>
                    {result.description && (
                      <p className="text-sm text-gray-500 line-clamp-2">{result.description}</p>
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
            className="text-sm text-primary-600 hover:underline mb-4"
          >
            返回框架列表
          </button>
          <h2 className="text-lg font-semibold mb-3">{selectedFw.name}</h2>
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
