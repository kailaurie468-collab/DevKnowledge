import { api } from './client'
import type { RerankerConfig, RerankerConfigRequest } from '@/types/api'

export const rerankerApi = {
  getActiveConfig: () =>
    api.get<RerankerConfig>('/user/reranker-config'),

  getAllConfigs: () =>
    api.get<RerankerConfig[]>('/user/reranker-configs'),

  updateConfig: (data: RerankerConfigRequest) =>
    api.put<RerankerConfig>('/user/reranker-config', data),

  switchConfig: (id: string) =>
    api.post(`/user/reranker-configs/${id}/activate`),

  deleteConfig: (id: string) =>
    api.delete(`/user/reranker-configs/${id}`),

  testConfig: () =>
    api.post<{ success: boolean; message: string }>('/user/reranker-config/test'),
}
