import { api } from './client'
import type { AiConfig, AiConfigRequest, ProviderInfo, TokenUsage } from '@/types/api'

export const settingsApi = {
  getActiveConfig: () =>
    api.get<AiConfig>('/user/ai-config'),

  getAllConfigs: () =>
    api.get<AiConfig[]>('/user/ai-configs'),

  updateAiConfig: (data: AiConfigRequest) =>
    api.put<AiConfig>('/user/ai-config', data),

  switchConfig: (id: string) =>
    api.post(`/user/ai-configs/${id}/activate`),

  deleteConfig: (id: string) =>
    api.delete(`/user/ai-configs/${id}`),

  testAiConfig: () =>
    api.post<{ success: boolean; message: string }>('/user/ai-config/test'),

  getTokenUsage: () =>
    api.get<TokenUsage[]>('/user/token-usage'),

  getProviders: () =>
    api.get<ProviderInfo[]>('/providers'),
}
