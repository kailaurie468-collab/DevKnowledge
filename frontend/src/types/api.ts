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
  maxIterations?: number
  kbId?: string
  topK?: number
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
  embeddingModel?: string
  embeddingDimensions?: number
  createdAt: string
  updatedAt: string
}

export interface KbDocument {
  id: string
  kbId: string
  filename: string
  fileType: string
  fileSize: number
  content?: string
  status: 'processing' | 'ready' | 'error' | 'embedding'
  errorMessage?: string
  chunkCount?: number
  createdAt: string
}

export interface KbChunk {
  id: string
  content: string
  similarity: number
}

// AI Config
export interface AiConfig {
  id?: string
  name?: string
  provider: 'openai' | 'anthropic' | 'deepseek' | 'custom'
  apiKeyMasked: string
  baseUrl: string
  model: string
  maxTokens: number
  isActive?: boolean
}

export interface AiConfigRequest {
  configId?: string
  name?: string
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

export interface TokenUsage {
  date: string
  tokens: number
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

// Embedding Config
export interface EmbeddingConfig {
  id?: string
  name?: string
  apiKeyMasked: string
  baseUrl: string
  isActive?: boolean
}

export interface EmbeddingConfigRequest {
  configId?: string
  name?: string
  apiKey: string
  baseUrl: string
}

// RAG Metrics
export interface RagMetric {
  demoId: string
  demoTitle: string
  kbId: string
  ragUsed: boolean
  topK: number
  chunkCount: number
  avgSimilarity: number
  maxSimilarity: number
  minSimilarity: number
  retrievalMs: number
  toolCallCount: number
  createdAt: string
}
