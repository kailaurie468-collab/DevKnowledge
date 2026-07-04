import { STEP_TYPE_LABELS } from '@/types/skills'
import type { Skill } from '@/types/skills'

interface SkillCardProps {
  skill: Skill
  selected?: boolean
  onSelect: (skill: Skill) => void
  onExport: (id: string) => void
  onDelete: (id: string) => void
}

/**
 * Skill 列表卡片组件
 * 展示 Skill 摘要信息，支持选中、导出、删除操作
 */
export function SkillCard({ skill, selected, onSelect, onExport, onDelete }: SkillCardProps) {
  // 格式化更新时间
  const timeAgo = formatTimeAgo(skill.updatedAt)

  return (
    <div
      onClick={() => onSelect(skill)}
      className={`p-4 border rounded-lg cursor-pointer transition-all ${
        selected
          ? 'border-primary-400 bg-primary-50 dark:border-primary-500 dark:bg-primary-900/20'
          : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-primary-300 dark:hover:border-primary-600'
      }`}
    >
      {/* 头部：名称 + 分类 */}
      <div className="flex items-start justify-between gap-2 mb-2">
        <h3 className="font-medium text-sm text-gray-900 dark:text-gray-100 line-clamp-1">
          {skill.name}
        </h3>
        <div className="flex items-center gap-2 shrink-0">
          {skill.category && (
            <span className="text-xs px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 rounded">
              {skill.category}
            </span>
          )}
          <span className="text-xs text-gray-400 dark:text-gray-500">
            v{skill.version}
          </span>
        </div>
      </div>

      {/* 描述 */}
      {skill.description && (
        <p className="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 mb-3">
          {skill.description}
        </p>
      )}

      {/* 步骤预览 */}
      <div className="flex items-center gap-3 mb-3">
        <span className="text-xs text-gray-400 dark:text-gray-500">
          {skill.steps.length} 个步骤
        </span>
        <div className="flex gap-1 flex-wrap">
          {skill.steps.slice(0, 3).map((step) => (
            <span
              key={step.id}
              className="text-xs px-1 py-0.5 bg-gray-50 dark:bg-gray-700 text-gray-500 dark:text-gray-400 rounded"
            >
              {STEP_TYPE_LABELS[step.stepType] || step.stepType}
            </span>
          ))}
          {skill.steps.length > 3 && (
            <span className="text-xs text-gray-400">+{skill.steps.length - 3}</span>
          )}
        </div>
      </div>

      {/* 底部：时间 + 操作 */}
      <div className="flex items-center justify-between">
        <span className="text-xs text-gray-400 dark:text-gray-500">{timeAgo}</span>
        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
          <button
            onClick={() => onExport(skill.id)}
            className="px-2 py-1 text-xs text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded transition-colors"
            title="导出 Markdown"
          >
            导出
          </button>
          <button
            onClick={() => onDelete(skill.id)}
            className="px-2 py-1 text-xs text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors"
            title="删除"
          >
            删除
          </button>
        </div>
      </div>
    </div>
  )
}

/** 相对时间格式化 */
function formatTimeAgo(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHour / 24)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  if (diffHour < 24) return `${diffHour} 小时前`
  if (diffDay < 30) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN')
}
