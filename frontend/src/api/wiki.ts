import { api } from './client'
import type {
  WikiUploadResponse,
  WikiIndexEntry,
  WikiGraphData,
  WikiLintResult,
} from '@/types/wiki'

/**
 * 带认证的 fetch 包装，自动处理 401
 */
async function authFetch(url: string, init?: RequestInit): Promise<Response> {
  const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken')
  const headers: Record<string, string> = {
    ...(init?.headers as Record<string, string>),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(url, { ...init, headers })

  if (response.status === 401) {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('refreshToken')
    window.location.href = '/login'
    throw new Error('Unauthorized')
  }

  return response
}

export const wikiApi = {
  /**
   * 上传单个文档（需要 FormData，不能用 api 客户端）
   */
  uploadDocument: async (file: File): Promise<WikiUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    const res = await authFetch('/api/wiki/upload', {
      method: 'POST',
      body: formData,
    })
    if (!res.ok) {
      const error = await res.json().catch(() => ({ message: res.statusText }))
      throw new Error(error.message || `上传失败 (${res.status})`)
    }
    return res.json()
  },

  /**
   * 上传 Obsidian vault 目录（需要 FormData，不能用 api 客户端）
   */
  uploadVault: async (files: File[]): Promise<WikiUploadResponse[]> => {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    const res = await authFetch('/api/wiki/upload-vault', {
      method: 'POST',
      body: formData,
    })
    if (!res.ok) {
      const error = await res.json().catch(() => ({ message: res.statusText }))
      throw new Error(error.message || `上传失败 (${res.status})`)
    }
    return res.json()
  },

  /**
   * 获取页面列表
   */
  getPages: (category?: string) => {
    const params = category ? `?category=${category}` : ''
    return api.get<WikiIndexEntry[]>(`/wiki/pages${params}`)
  },

  /**
   * 读取页面内容（返回纯文本，不能用 api 客户端的 JSON 解析）
   */
  getPage: async (path: string): Promise<string> => {
    const res = await authFetch(`/api/wiki/page?path=${encodeURIComponent(path)}`)
    if (!res.ok) {
      const error = await res.text().catch(() => res.statusText)
      throw new Error(error || `加载失败 (${res.status})`)
    }
    return res.text()
  },

  /**
   * 获取图谱数据
   */
  getGraph: () =>
    api.get<WikiGraphData>('/wiki/graph'),

  /**
   * 触发深度分析
   */
  analyzeDocument: (docId: string) =>
    api.post<WikiUploadResponse>(`/wiki/analyze/${docId}`),

  /**
   * Wiki 健康检查
   */
  lint: () =>
    api.post<WikiLintResult>('/wiki/lint'),

  /**
   * 删除文档
   */
  deleteDocument: (docId: string) =>
    api.delete<void>(`/wiki/doc/${docId}`),
}
