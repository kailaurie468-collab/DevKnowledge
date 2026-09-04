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
  errorDetail?: string
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

export interface AdminSpan {
  stage: string
  status: string
  durationMs: number
  createdAt: string
}

export interface AdminTraceDetail {
  trace: AdminRequestTrace | null
  spans: AdminSpan[]
}

export interface AdminUser {
  id: string
  email: string
  displayName?: string
  createdAt: string
  lastActiveAt?: string
  totalTokens: number
  demoCount: number
  feedbackCount: number
}

export type FeedbackStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED'

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
  errorDetail: (id: string) => api.get<AdminError>(`/admin/errors/${id}`),
  traceDetail: (requestId: string) =>
    api.get<AdminTraceDetail>('/admin/traces/detail', { requestId }),
  users: (page = 1, size = 20) =>
    api.get<AdminPageResponse<AdminUser>>('/admin/users', {
      page: String(page),
      size: String(size),
    }),
  feedback: (page = 1, size = 20, status?: FeedbackStatus) =>
    api.get<AdminPageResponse<AdminFeedback>>('/admin/feedback', {
      page: String(page),
      size: String(size),
      ...(status ? { status } : {}),
    }),
  updateFeedbackStatus: (id: string, status: FeedbackStatus) =>
    api.patch(`/admin/feedback/${id}/status`, { status }),
}
