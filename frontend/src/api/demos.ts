import { api } from './client'
import type { Demo, GenerateDemoRequest } from '@/types/api'

export const demosApi = {
  generate: (request: GenerateDemoRequest) =>
    (signal: AbortSignal) => api.stream('/demos/generate', request, signal),

  getDemos: () =>
    api.get<Demo[]>('/demos'),

  getDemo: (id: string) =>
    api.get<Demo>(`/demos/${id}`),

  deleteDemo: (id: string) =>
    api.delete<void>(`/demos/${id}`),
}
