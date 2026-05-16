import { api } from './client'
import type { Demo, GenerateDemoRequest } from '@/types/api'

export interface DemoPageParams {
  page?: number
  size?: number
  keyword?: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pages: number
  size: number
  current: number
}

export const demosApi = {
  generate: (request: GenerateDemoRequest) =>
    (signal: AbortSignal) => api.stream('/demos/generate', request, signal),

  getDemos: (params?: DemoPageParams) => {
    const qs = new URLSearchParams()
    if (params?.page) qs.set('page', String(params.page))
    if (params?.size) qs.set('size', String(params.size))
    if (params?.keyword) qs.set('keyword', params.keyword)
    const query = qs.toString()
    return api.get<PageResponse<Demo>>(`/demos${query ? '?' + query : ''}`)
  },

  getDemo: (id: string) =>
    api.get<Demo>(`/demos/${id}`),

  deleteDemo: (id: string) =>
    api.delete<void>(`/demos/${id}`),
}
