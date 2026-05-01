import { api } from './client'
import type { AiConfig, AiConfigRequest, ProviderInfo } from '@/types/api'

export const settingsApi = {
  getAiConfig: () =>
    api.get<AiConfig>('/user/ai-config'),

  updateAiConfig: (data: AiConfigRequest) =>
    api.put<AiConfig>('/user/ai-config', data),

  testAiConfig: () =>
    api.post<{ success: boolean; message: string }>('/user/ai-config/test'),

  getProviders: () =>
    api.get<ProviderInfo[]>('/providers'),
}
