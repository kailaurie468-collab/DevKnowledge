import { api } from './client'
import type { KnowledgeBase, KbDocument, KbChunk } from '@/types/api'

export const kbApi = {
  createKb: (data: { name: string; description?: string }) =>
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

  getDocuments: (kbId: string) =>
    api.get<KbDocument[]>(`/kb/${kbId}/documents`),

  deleteDocument: (docId: string) =>
    api.delete<void>(`/kb/documents/${docId}`),

  searchKb: (kbId: string, query: string) =>
    api.get<KbChunk[]>(`/kb/${kbId}/search`, { q: query }),
}
