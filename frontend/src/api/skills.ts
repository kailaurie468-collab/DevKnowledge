import { api } from './client'
import type {
  Skill,
  SkillSuggestion,
  ExtractSkillRequest,
  SkillQueryParams,
  SkillUpdateRequest,
  ActivityRequest,
} from '@/types/skills'

export const skillsApi = {
  // ---- Skill 提取 (SSE) ----

  extract: (request: ExtractSkillRequest) =>
    (signal: AbortSignal) => api.stream('/skills/extract', request, signal),

  // ---- Skill CRUD ----

  /** 获取 Skills 列表，支持搜索和过滤 */
  getSkills: (params?: SkillQueryParams) => {
    const query: Record<string, string> = {}
    if (params?.category) query.category = params.category
    if (params?.keyword) query.keyword = params.keyword
    if (params?.frameworkId) query.frameworkId = params.frameworkId
    if (params?.page !== undefined) query.page = String(params.page)
    if (params?.size !== undefined) query.size = String(params.size)
    return api.get<{ records: Skill[] }>('/skills', Object.keys(query).length ? query : undefined)
      .then(page => page?.records ?? [])
  },

  getSkill: (id: string) =>
    api.get<Skill>(`/skills/${id}`),

  updateSkill: (id: string, data: SkillUpdateRequest) =>
    api.put<Skill>(`/skills/${id}`, data),

  deleteSkill: (id: string) =>
    api.delete<void>(`/skills/${id}`),

  // ---- Skill 导出 ----

  exportSkill: (id: string) =>
    api.post<{ content: string }>(`/skills/${id}/export`),

  downloadSkill: (id: string) => {
    const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken')
    window.open(`/api/skills/${id}/export/download?token=${token}`, '_blank')
  },

  // ---- Skill Suggestions (智能推荐) ----

  getSuggestions: () =>
    api.get<SkillSuggestion[]>('/skills/suggestions'),

  refreshSuggestions: () =>
    api.post<void>('/skills/suggestions/refresh'),

  updateSuggestion: (id: string, data: Partial<SkillSuggestion>) =>
    api.put<SkillSuggestion>(`/skills/suggestions/${id}`, data),

  acceptSuggestion: (id: string) =>
    api.post<Skill>(`/skills/suggestions/${id}/accept`),

  dismissSuggestion: (id: string) =>
    api.post<void>(`/skills/suggestions/${id}/dismiss`),

  // ---- Activities (行为记录) ----

  recordActivity: (data: ActivityRequest) =>
    api.post<void>('/activities', data),

  getActivities: (params?: { page?: number; size?: number }) => {
    const query: Record<string, string> = {}
    if (params?.page !== undefined) query.page = String(params.page)
    if (params?.size !== undefined) query.size = String(params.size)
    return api.get<unknown[]>('/activities', Object.keys(query).length ? query : undefined)
  },

  cleanupActivities: () =>
    api.delete<number>('/activities/cleanup'),
}
