export interface ClientErrorPayload {
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

const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'dev'

/**
 * 发送前端错误摘要。使用独立 fetch 避免依赖 ApiClient，防止上报失败形成递归。
 */
export function reportClientError(payload: ClientErrorPayload) {
  const body = {
    ...payload,
    errorSummary: sanitize(payload.errorSummary),
    page: payload.page || window.location.pathname,
    appVersion: payload.appVersion || APP_VERSION,
    userAgent: payload.userAgent || navigator.userAgent,
    environment: payload.environment || import.meta.env.MODE,
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Client-Version': body.appVersion,
    // 上报请求使用新 ID，body 中的 requestId 保留原始失败请求的关联 ID
    'X-Request-Id': crypto.randomUUID(),
  }
  const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  void fetch('/api/telemetry/errors', {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    keepalive: true,
  }).catch(() => {
    // 上报失败不能影响当前用户操作
  })
}

function sanitize(value: string) {
  return (value || '未知前端错误')
    .replace(/Bearer\s+[^\s]+/gi, 'Bearer [REDACTED]')
    .replace(/\b(api[-_ ]?key|password|secret)\s*[:=]\s*[^\s,;]+/gi, '$1=[REDACTED]')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 2000)
}
