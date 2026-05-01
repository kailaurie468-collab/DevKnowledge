import { useState, useEffect, useCallback } from 'react'
import { knowledgeApi } from '@/api/knowledge'
import { SearchBar } from '@/components/knowledge/SearchBar'
import { FrameworkGrid } from '@/components/knowledge/FrameworkGrid'
import { LinkCard } from '@/components/knowledge/LinkCard'
import type { Framework, KnowledgeLink, LinkSearchResult } from '@/types/api'

export function KnowledgePage() {
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [selectedFw, setSelectedFw] = useState<Framework | null>(null)
  const [links, setLinks] = useState<KnowledgeLink[]>([])
  const [searchResults, setSearchResults] = useState<LinkSearchResult[]>([])
  const [isSearching, setIsSearching] = useState(false)

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
      setIsSearching(false)
      return
    }
    setIsSearching(true)
    try {
      const results = await knowledgeApi.searchLinks(query)
      setSearchResults(results)
    } catch (err) {
      console.error(err)
    } finally {
      setIsSearching(false)
    }
  }, [])

  const showSearch = searchResults.length > 0 || isSearching

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-4">知识搜索</h1>

      <div className="mb-6">
        <SearchBar onSearch={handleSearch} placeholder="搜索文档... 例如 React useEffect" />
      </div>

      {showSearch ? (
        <div>
          <h2 className="text-sm font-medium text-gray-500 mb-3">
            {isSearching ? '搜索中...' : `${searchResults.length} 条结果`}
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {searchResults.map(result => (
              <LinkCard key={result.link.id} result={result} />
            ))}
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
