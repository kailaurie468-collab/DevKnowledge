import { api } from './client'
import type { Framework, KnowledgeLink, LinkSearchResult } from '@/types/api'

export const knowledgeApi = {
  getFrameworks: () =>
    api.get<Framework[]>('/frameworks'),

  getFrameworkLinks: (slug: string) =>
    api.get<KnowledgeLink[]>(`/frameworks/${slug}/links`),

  searchLinks: (query: string) =>
    api.get<LinkSearchResult[]>('/links/search', { q: query }),
}
