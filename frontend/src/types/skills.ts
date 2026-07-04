/**
 * Skills 模块专用类型定义
 * 补充 api.ts 中已有的 Skill/SkillStep/SkillSuggestion 类型
 */

// 从 api.ts 复用核心类型
export type { Skill, SkillStep, SkillSuggestion, ExtractSkillRequest } from './api'

// ---- 查询参数 ----

/** Skills 列表查询参数 */
export interface SkillQueryParams {
  category?: string
  keyword?: string
  frameworkId?: string
  page?: number
  size?: number
}

// ---- 更新请求 ----

/** Skill 更新请求体 */
export interface SkillUpdateRequest {
  name?: string
  description?: string
  category?: string
  triggerDescription?: string
  steps?: SkillStepUpdate[]
}

/** Skill 步骤更新项（有 id 为更新，无 id 为新增） */
export interface SkillStepUpdate {
  id?: string
  stepOrder: number
  title: string
  description: string
  stepType: 'action' | 'decision' | 'validation' | 'reference'
  codeTemplate?: string
  expectedOutput?: string
  notes?: string
}

// ---- 行为记录 ----

/** 行为记录请求体 */
export interface ActivityRequest {
  type: ActivityType
  framework?: string
  keywords?: string[]
  language?: string
  resultCount?: number
  metadata?: Record<string, unknown>
}

/** 行为类型枚举 */
export type ActivityType =
  | 'demo_generate'
  | 'kb_search'
  | 'link_click'
  | 'skill_extract'
  | 'skill_export'

// ---- 存储模式 ----

/** 行为数据存储模式 */
export type StorageMode = 'server' | 'local' | 'hybrid'

// ---- 分类 ----

/** 预设分类列表 */
export const SKILL_CATEGORIES = [
  'frontend',
  'backend',
  'devops',
  'database',
  'testing',
  'other',
] as const

export type SkillCategory = (typeof SKILL_CATEGORIES)[number]

// ---- 步骤类型 ----

/** 步骤类型枚举及中文映射 */
export const STEP_TYPE_LABELS: Record<string, string> = {
  action: '操作',
  decision: '决策',
  validation: '验证',
  reference: '参考',
}
