export interface ApiResponse<T> {
  data: T
  message?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// Auth
export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  displayName?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

// User
export interface User {
  id: string
  email: string
  displayName?: string
}

// Framework
export interface Framework {
  id: string
  name: string
  slug: string
  baseUrl: string
  iconUrl?: string
  description?: string
  category: 'frontend' | 'backend' | 'mobile'
}

// Knowledge Link
export interface KnowledgeLink {
  id: string
  frameworkId: string
  title: string
  url: string
  anchor?: string
  description?: string
  tags: string[]
  popularityScore: number
}

export interface LinkSearchResult {
  link: KnowledgeLink
  frameworkName: string
  relevanceScore: number
}

// Demo
export interface GenerateDemoRequest {
  prompt: string
  frameworkId?: string
  language?: string
  kbId?: string
}

export interface Demo {
  id: string
  title: string
  prompt: string
  codeContent: string
  explanation: string
  language: string
  tags: string[]
  tokensUsed?: number
  modelVersion?: string
  createdAt: string
}

// Skill
export interface ExtractSkillRequest {
  description: string
  frameworkId?: string
  category?: string
}

export interface SkillStep {
  id: string
  stepOrder: number
  title: string
  description: string
  stepType: 'action' | 'decision' | 'validation' | 'reference'
  codeTemplate?: string
  expectedOutput?: string
  notes?: string
}

export interface Skill {
  id: string
  name: string
  description: string
  category?: string
  frameworkId?: string
  triggerDescription: string
  exportedContent?: string
  version: number
  isPublic: boolean
  steps: SkillStep[]
  createdAt: string
  updatedAt: string
}

// Skill Suggestion (智能推荐)
export interface SkillSuggestion {
  id: string
  name: string
  description: string
  triggerDescription: string
  category?: string
  suggestedSteps: Omit<SkillStep, 'id'>[]
  sourceSummary: string
  status: 'pending' | 'accepted' | 'dismissed'
  createdAt: string
  updatedAt: string
}

// Knowledge Base
export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  documentCount?: number
  createdAt: string
  updatedAt: string
}

export interface KbDocument {
  id: string
  filename: string
  fileType: string
  chunkCount: number
  createdAt: string
}

export interface KbChunk {
  id: string
  content: string
  similarity: number
}

// AI Config
export interface AiConfig {
  provider: 'openai' | 'anthropic' | 'deepseek' | 'custom'
  apiKeyMasked: string
  baseUrl: string
  model: string
  maxTokens: number
}

export interface AiConfigRequest {
  provider: string
  apiKey: string
  baseUrl: string
  model: string
  maxTokens?: number
}

export interface ProviderInfo {
  name: string
  defaultBaseUrl: string
  models: string[]
}

// SSE — 支持 ReAct 推理过程可视化
export type SSEEventType =
  | 'thought'      // AI 推理过程
  | 'tool_call'    // 工具调用请求
  | 'tool_result'  // 工具返回结果
  | 'code'         // 代码块
  | 'explanation'  // 解释文本
  | 'metadata'     // 元数据
  | 'done'         // 完成
  | 'error'        // 错误

export interface SSEEvent {
  type: SSEEventType
  content: string
  functionName?: string  // tool_call 时的函数名
}
