import { api } from './client'
import type { KnowledgeBase, KbDocument } from '@/types/api'

export const kbApi = {
  createKb: (data: { name: string; description?: string; embeddingModel?: string; embeddingDimensions?: number }) =>
    api.post<KnowledgeBase>('/kb', data),

  getKbs: () =>
    api.get<KnowledgeBase[]>('/kb'),

  getKb: (id: string) =>
    api.get<KnowledgeBase>(`/kb/${id}`),

  deleteKb: (id: string) =>
    api.delete<void>(`/kb/${id}`),

  uploadDocument: (kbId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const token = localStorage.getItem('accessToken')
    return fetch(`/api/kb/${kbId}/documents`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<KbDocument>
    })
  },

  batchUpload: (kbId: string, files: File[]) => {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    const token = localStorage.getItem('accessToken')
    return fetch(`/api/kb/${kbId}/documents/batch`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json() as Promise<KbDocument[]>
    })
  },

  getDocuments: (kbId: string) =>
    api.get<KbDocument[]>(`/kb/${kbId}/documents`),

  deleteDocument: (docId: string) =>
    api.delete<void>(`/kb/documents/${docId}`),

  searchKb: (kbId: string, query: string) =>
    api.get<KbDocument[]>(`/kb/${kbId}/search`, { q: query }),
}
