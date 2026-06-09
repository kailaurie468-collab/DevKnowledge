import { api } from './client'
import type {
  WikiUploadResponse,
  WikiIndexEntry,
  WikiGraphData,
  WikiLintResult,
} from '@/types/wiki'

export const wikiApi = {
  /**
   * 上传单个文档
   */
  uploadDocument: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const token = localStorage.getItem('accessToken')
    return fetch('/api/wiki/upload', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<WikiUploadResponse>
    })
  },

  /**
   * 上传 Obsidian vault 目录
   */
  uploadVault: (files: File[]) => {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    const token = localStorage.getItem('accessToken')
    return fetch('/api/wiki/upload-vault', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<WikiUploadResponse[]>
    })
  },

  /**
   * 获取页面列表
   */
  getPages: (category?: string) => {
    const params = category ? `?category=${category}` : ''
    return api.get<WikiIndexEntry[]>(`/wiki/pages${params}`)
  },

  /**
   * 读取页面内容
   */
  getPage: (path: string) => {
    const token = localStorage.getItem('accessToken')
    return fetch(`/api/wiki/page?path=${encodeURIComponent(path)}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.text()
    })
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
