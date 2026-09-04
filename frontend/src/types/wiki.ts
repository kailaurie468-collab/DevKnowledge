// Wiki 知识图谱类型定义

export interface WikiDocument {
  id: string
  userId: string
  filename: string
  fileType: string
  fileSize: number
  status: 'processing' | 'ready' | 'error'
  errorMsg?: string
  sourceType: 'upload' | 'obsidian_vault' | 'kb_import'
  createdAt: string
}

export interface WikiEntity {
  id: string
  userId: string
  name: string
  type: 'concept' | 'framework' | 'api' | 'tool'
  description?: string
  pagePath?: string
  docId?: string
  createdAt: string
  updatedAt: string
}

export interface WikiRelation {
  id: string
  userId: string
  sourceId: string
  targetId: string
  relation: 'uses' | 'extends' | 'contradicts' | 'related_to'
  description?: string
  strength: number
  createdAt: string
}

export interface WikiIndexEntry {
  id: string
  userId: string
  pagePath: string
  title: string
  category: 'entity' | 'concept' | 'source' | 'summary'
  tags: string[]
  summary?: string
  docIds: string[]
  updatedAt: string
}

export interface WikiGraphData {
  entities: WikiGraphNode[]
  relations: WikiGraphEdge[]
}

export interface WikiGraphNode {
  id: string
  name: string
  type: string
  description?: string
  pagePath?: string
}

export interface WikiGraphEdge {
  sourceId: string
  targetId: string
  relation: string
  description?: string
  strength: number
}

export interface WikiUploadResponse {
  docId: string
  filename: string
  status: string
  message: string
}

/** 摄取失败的文档（供前端展示错误原因与重试） */
export interface WikiFailedDocument {
  docId: string
  filename: string
  status: string
  errorMsg?: string
  createdAt: string
}

export interface WikiLintResult {
  contradictions: string[]
  orphanPages: string[]
  missingLinks: { from: string; to: string; reason: string }[]
  suggestions: string[]
}
