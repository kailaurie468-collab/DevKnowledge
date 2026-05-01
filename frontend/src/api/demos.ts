import { api } from './client'
import type { Demo, GenerateDemoRequest } from '@/types/api'

export const demosApi = {
  generate: (request: GenerateDemoRequest) =>
    api.stream('/demos/generate', request),

  getDemos: () =>
    api.get<Demo[]>('/demos'),

  getDemo: (id: string) =>
    api.get<Demo>(`/demos/${id}`),

  deleteDemo: (id: string) =>
    api.delete<void>(`/demos/${id}`),
}
