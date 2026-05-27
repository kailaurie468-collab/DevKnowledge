import { api } from './client'
import type { EmbeddingConfig, EmbeddingConfigRequest, TokenUsage } from '@/types/api'

export const embeddingApi = {
  getActiveConfig: () =>
    api.get<EmbeddingConfig>('/user/embedding-config'),

  getAllConfigs: () =>
    api.get<EmbeddingConfig[]>('/user/embedding-configs'),

  updateConfig: (data: EmbeddingConfigRequest) =>
    api.put<EmbeddingConfig>('/user/embedding-config', data),

  switchConfig: (id: string) =>
    api.post(`/user/embedding-configs/${id}/activate`),

  deleteConfig: (id: string) =>
    api.delete(`/user/embedding-configs/${id}`),

  testConfig: () =>
    api.post<{ success: boolean; message: string }>('/user/embedding-config/test'),

  getTokenUsage: () =>
    api.get<TokenUsage[]>('/user/embedding-usage'),
}
