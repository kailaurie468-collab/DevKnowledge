import { reportClientError } from '@/utils/errorReporting'

const API_BASE = '/api'
const CLIENT_VERSION = import.meta.env.VITE_APP_VERSION || 'dev'

interface RequestConfig extends RequestInit {
  params?: Record<string, string>
}

class ApiClient {
  private getToken(): string | null {
    return localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken')
  }

  private async request<T>(endpoint: string, config: RequestConfig = {}): Promise<T> {
    const { params, ...init } = config
    let url = `${API_BASE}${endpoint}`

    if (params) {
      const search = new URLSearchParams(params).toString()
      url += `?${search}`
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Client-Version': CLIENT_VERSION,
      'X-Request-Id': crypto.randomUUID(),
      ...(init.headers as Record<string, string>),
    }

    const token = this.getToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const requestId = headers['X-Request-Id']
    let response: Response
    try {
      response = await fetch(url, { ...init, headers })
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        reportClientError({
          requestId,
          errorSummary: error instanceof Error ? error.message : '网络请求失败',
          errorDetail: error instanceof Error ? error.stack : undefined,
          errorType: 'NetworkError',
          stage: 'http',
        })
      }
      throw error
    }

    if (response.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      sessionStorage.removeItem('accessToken')
      sessionStorage.removeItem('refreshToken')
      window.location.href = '/login'
      throw new Error('Unauthorized')
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      const message = error.message || `HTTP ${response.status}`
      if (response.status >= 500 || response.status === 408 || response.status === 429) {
        reportClientError({
          requestId,
          errorSummary: message,
          errorType: `Http${response.status}`,
          stage: 'http',
        })
      }
      throw new Error(message)
    }

    if (response.status === 204) {
      return undefined as T
    }

    const text = await response.text()
    if (!text) return undefined as T
    return JSON.parse(text)
  }

  get<T>(endpoint: string, params?: Record<string, string>) {
    return this.request<T>(endpoint, { method: 'GET', params })
  }

  post<T>(endpoint: string, body?: unknown) {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  put<T>(endpoint: string, body?: unknown) {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  delete<T>(endpoint: string) {
    return this.request<T>(endpoint, { method: 'DELETE' })
  }

  patch<T>(endpoint: string, body?: unknown) {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  async *stream(endpoint: string, body: unknown, signal?: AbortSignal): AsyncGenerator<{ event: string; data: string }> {
    const token = this.getToken()
    const requestId = crypto.randomUUID()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Client-Version': CLIENT_VERSION,
      'X-Request-Id': requestId,
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    let response: Response
    try {
      response = await fetch(`${API_BASE}${endpoint}`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal,
      })
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        reportClientError({
          requestId,
          errorSummary: error instanceof Error ? error.message : 'SSE 网络请求失败',
          errorDetail: error instanceof Error ? error.stack : undefined,
          errorType: 'SSENetworkError',
          stage: 'sse',
        })
      }
      throw error
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      const message = error.message || `HTTP ${response.status}`
      if (response.status >= 500 || response.status === 408 || response.status === 429) {
        reportClientError({
          requestId,
          errorSummary: message,
          errorType: `SSEHttp${response.status}`,
          stage: 'sse',
        })
      }
      throw new Error(message)
    }

    if (!response.body) throw new Error('No response body')

    const { EventSourceParserStream } = await import('eventsource-parser/stream')
    const eventStream = response.body
      .pipeThrough(new TextDecoderStream())
      .pipeThrough(new EventSourceParserStream())

    const reader = eventStream.getReader()
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        const data = value.data
        if (data === '[DONE]') return
        if (data) yield { event: value.event || 'message', data }
      }
    } finally {
      reader.releaseLock()
    }
  }
}

export const api = new ApiClient()
