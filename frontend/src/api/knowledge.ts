import { api } from './client'
import type { Framework, KnowledgeLink, LinkSearchResult } from '@/types/api'

export interface WebSearchResult {
  title: string
  url: string
  description: string
}

export const knowledgeApi = {
  getFrameworks: () =>
    api.get<Framework[]>('/frameworks'),

  getFrameworkLinks: (slug: string) =>
    api.get<KnowledgeLink[]>(`/frameworks/${slug}/links`),

  searchLinks: (query: string, framework?: string) =>
    api.get<LinkSearchResult[]>('/links/search', framework
      ? { q: query, framework }
      : { q: query }),

  /** Web 搜索（实时联网） */
  webSearch: (query: string, limit = 10) =>
    api.get<WebSearchResult[]>('/links/web-search', { q: query, limit: String(limit) }),
}
