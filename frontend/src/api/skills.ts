import { api } from './client'
import type { Skill, ExtractSkillRequest } from '@/types/api'

export const skillsApi = {
  extract: (request: ExtractSkillRequest) =>
    api.stream('/skills/extract', request),

  getSkills: () =>
    api.get<Skill[]>('/skills'),

  getSkill: (id: string) =>
    api.get<Skill>(`/skills/${id}`),

  updateSkill: (id: string, data: Partial<Skill>) =>
    api.put<Skill>(`/skills/${id}`, data),

  deleteSkill: (id: string) =>
    api.delete<void>(`/skills/${id}`),

  exportSkill: (id: string) =>
    api.post<{ content: string }>(`/skills/${id}/export`),

  downloadSkill: (id: string) => {
    const token = localStorage.getItem('accessToken')
    window.open(`/api/skills/${id}/export/download?token=${token}`, '_blank')
  },
}
