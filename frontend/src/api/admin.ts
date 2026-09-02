import { api } from './client'

export interface AdminOverview {
  totalUsers: number
  totalTokens: number
  totalRequests: number
  successfulRequests: number
  successRate: number
  averageLatencyMs: number
  p95LatencyMs: number
  errorCount: number
  feedbackCount: number
}

export interface AdminError {
  id: string
  requestId?: string
  userId?: string
  source: string
  stage?: string
  errorType?: string
  errorSummary: string
  method?: string
  path?: string
  page?: string
  appVersion?: string
  userAgent?: string
  environment?: string
  durationMs?: number
  createdAt: string
}

export interface AdminFeedback {
  id: string
  requestId?: string
  userId?: string
  feedbackType: string
  content: string
  contact?: string
  page?: string
  status: string
  createdAt: string
}

export interface AdminRequestTrace {
  requestId: string
  method: string
  path: string
  statusCode?: number
  outcome: string
  totalMs: number
  firstEventMs?: number
  firstTextMs?: number
  createdAt: string
}

export interface AdminPageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export const adminApi = {
  overview: () => api.get<AdminOverview>('/admin/overview'),
  traces: (page = 1, size = 20) =>
    api.get<AdminPageResponse<AdminRequestTrace>>('/admin/traces', {
      page: String(page),
      size: String(size),
    }),
  errors: (limit = 50) => api.get<AdminError[]>('/admin/errors', { limit: String(limit) }),
  feedback: (limit = 50) => api.get<AdminFeedback[]>('/admin/feedback', { limit: String(limit) }),
}
