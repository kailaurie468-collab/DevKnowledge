import { useState, useCallback } from 'react'
import { SKILL_CATEGORIES, STEP_TYPE_LABELS } from '@/types/skills'
import type { Skill, SkillStep, SkillUpdateRequest } from '@/types/skills'

interface SkillEditorProps {
  skill: Skill
  onSave: (id: string, data: SkillUpdateRequest) => Promise<void>
  onCancel: () => void
  onExport: (id: string) => void
}

/**
 * Skill 在线编辑器组件
 * 支持编辑 name/description/category/triggerDescription/steps
 */
export function SkillEditor({ skill, onSave, onCancel, onExport }: SkillEditorProps) {
  const [name, setName] = useState(skill.name)
  const [description, setDescription] = useState(skill.description || '')
  const [category, setCategory] = useState(skill.category || '')
  const [triggerDescription, setTriggerDescription] = useState(skill.triggerDescription || '')
  const [steps, setSteps] = useState<EditStep[]>(
    skill.steps.map((s) => ({ ...s, _editing: false }))
  )
  const [saving, setSaving] = useState(false)

  // 更新步骤字段
  const updateStep = useCallback((index: number, field: keyof SkillStep, value: string) => {
    setSteps((prev) =>
      prev.map((s, i) => (i === index ? { ...s, [field]: value } : s))
    )
  }, [])

  // 添加新步骤
  const addStep = useCallback(() => {
    setSteps((prev) => [
      ...prev,
      {
        id: '',
        stepOrder: prev.length + 1,
        title: '',
        description: '',
        stepType: 'action',
        codeTemplate: '',
        expectedOutput: '',
        notes: '',
        _editing: true,
      },
    ])
  }, [])

  // 删除步骤
  const removeStep = useCallback((index: number) => {
    setSteps((prev) =>
      prev
        .filter((_, i) => i !== index)
        .map((s, i) => ({ ...s, stepOrder: i + 1 }))
    )
  }, [])

  // 切换步骤编辑模式
  const toggleStepEdit = useCallback((index: number) => {
    setSteps((prev) =>
      prev.map((s, i) => (i === index ? { ...s, _editing: !s._editing } : s))
    )
  }, [])

  // 保存
  const handleSave = async () => {
    if (!name.trim()) return
    setSaving(true)
    try {
      await onSave(skill.id, {
        name: name.trim(),
        description: description.trim(),
        category: category || undefined,
        triggerDescription: triggerDescription.trim(),
        steps: steps.map((s, i) => ({
          id: s.id || undefined,
          stepOrder: i + 1,
          title: s.title,
          description: s.description,
          stepType: s.stepType,
          codeTemplate: s.codeTemplate || undefined,
          expectedOutput: s.expectedOutput || undefined,
          notes: s.notes || undefined,
        })),
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 overflow-hidden">
      {/* 头部 */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-400 dark:text-gray-500">编辑 Skill</span>
          <span className="text-xs text-gray-400 dark:text-gray-500">v{skill.version}</span>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => onExport(skill.id)}
            className="text-xs text-primary-600 dark:text-primary-400 hover:underline"
          >
            导出 .md
          </button>
          <button
            onClick={onCancel}
            className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            关闭
          </button>
        </div>
      </div>

      {/* 编辑表单 */}
      <div className="p-4 space-y-4 max-h-[70vh] overflow-y-auto">
        {/* 名称 */}
        <div>
          <label className="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
            名称 <span className="text-red-400">*</span>
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        {/* 描述 */}
        <div>
          <label className="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
            描述
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="w-full px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
          />
        </div>

        {/* 分类 + 触发条件 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
              分类
            </label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">未分类</option>
              {SKILL_CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
              触发条件
            </label>
            <input
              type="text"
              value={triggerDescription}
              onChange={(e) => setTriggerDescription(e.target.value)}
              placeholder="何时使用此 Skill"
              className="w-full px-3 py-1.5 border border-gray-300 dark:border-gray-600 rounded-md text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>

        {/* 步骤列表 */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="text-xs font-medium text-gray-500 dark:text-gray-400">
              步骤 ({steps.length})
            </label>
            <button
              onClick={addStep}
              className="text-xs text-primary-600 dark:text-primary-400 hover:underline"
            >
              + 添加步骤
            </button>
          </div>

          <div className="space-y-2">
            {steps.map((step, i) => (
              <div
                key={i}
                className="border border-gray-200 dark:border-gray-700 rounded-md overflow-hidden"
              >
                {/* 步骤摘要（非编辑模式） */}
                {!step._editing && (
                  <button
                    type="button"
                    onClick={() => toggleStepEdit(i)}
                    className="w-full text-left p-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-gray-400 dark:text-gray-500 shrink-0">
                        #{step.stepOrder}
                      </span>
                      <span className="text-xs px-1 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 rounded shrink-0">
                        {STEP_TYPE_LABELS[step.stepType] || step.stepType}
                      </span>
                      <span className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                        {step.title || '(无标题)'}
                      </span>
                    </div>
                    {step.description && (
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1 line-clamp-1 ml-12">
                        {step.description}
                      </p>
                    )}
                  </button>
                )}

                {/* 步骤编辑（编辑模式） */}
                {step._editing && (
                  <div className="p-3 space-y-2 bg-gray-50/50 dark:bg-gray-800/30">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono text-gray-400">#{step.stepOrder}</span>
                      <div className="flex gap-1">
                        <button
                          onClick={() => toggleStepEdit(i)}
                          className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                        >
                          收起
                        </button>
                        <button
                          onClick={() => removeStep(i)}
                          className="text-xs text-red-400 hover:text-red-600 ml-2"
                        >
                          删除
                        </button>
                      </div>
                    </div>

                    <div className="grid grid-cols-[1fr_auto] gap-2">
                      <input
                        type="text"
                        value={step.title}
                        onChange={(e) => updateStep(i, 'title', e.target.value)}
                        placeholder="步骤标题"
                        className="px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-primary-500"
                      />
                      <select
                        value={step.stepType}
                        onChange={(e) => updateStep(i, 'stepType', e.target.value)}
                        className="px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-xs bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100"
                      >
                        {Object.entries(STEP_TYPE_LABELS).map(([val, label]) => (
                          <option key={val} value={val}>
                            {label}
                          </option>
                        ))}
                      </select>
                    </div>

                    <textarea
                      value={step.description}
                      onChange={(e) => updateStep(i, 'description', e.target.value)}
                      placeholder="步骤描述"
                      rows={2}
                      className="w-full px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                    />

                    <textarea
                      value={step.codeTemplate || ''}
                      onChange={(e) => updateStep(i, 'codeTemplate', e.target.value)}
                      placeholder="代码模板（可选）"
                      rows={3}
                      className="w-full px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-xs font-mono bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
                    />

                    <input
                      type="text"
                      value={step.expectedOutput || ''}
                      onChange={(e) => updateStep(i, 'expectedOutput', e.target.value)}
                      placeholder="预期输出（可选）"
                      className="w-full px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-xs bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-primary-500"
                    />

                    <input
                      type="text"
                      value={step.notes || ''}
                      onChange={(e) => updateStep(i, 'notes', e.target.value)}
                      placeholder="补充说明（可选）"
                      className="w-full px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-xs bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-primary-500"
                    />
                  </div>
                )}
              </div>
            ))}

            {steps.length === 0 && (
              <p className="text-xs text-gray-400 dark:text-gray-500 text-center py-4">
                暂无步骤，点击上方"添加步骤"开始
              </p>
            )}
          </div>
        </div>
      </div>

      {/* 底部操作栏 */}
      <div className="flex items-center justify-end gap-2 px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
        <button
          onClick={onCancel}
          className="px-4 py-1.5 text-sm border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          取消
        </button>
        <button
          onClick={handleSave}
          disabled={saving || !name.trim()}
          className="px-4 py-1.5 text-sm bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 transition-colors"
        >
          {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  )
}

/** 内部编辑用步骤类型（带编辑状态标记） */
type EditStep = SkillStep & { _editing: boolean }
