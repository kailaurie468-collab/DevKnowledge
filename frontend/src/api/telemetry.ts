import { api } from './client'

export interface ClientErrorReportRequest {
  requestId?: string
  errorSummary: string
  errorType?: string
  stage?: string
  page?: string
  appVersion?: string
  userAgent?: string
  environment?: string
  durationMs?: number
}

export interface FeedbackRequest {
  feedbackType: string
  content: string
  contact?: string
  page?: string
  requestId?: string
}

export const telemetryApi = {
  reportError: (request: ClientErrorReportRequest) =>
    api.post<void>('/telemetry/errors', request),

  submitFeedback: (request: FeedbackRequest) =>
    api.post<void>('/feedback', request),
}
