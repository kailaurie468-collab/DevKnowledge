import { useState } from 'react'
import type { SkillSuggestion, SkillStep } from '@/types/api'

interface SkillSuggestionCardProps {
  suggestion: SkillSuggestion
  onAccept: (id: string) => void
  onDismiss: (id: string) => void
  onUpdate: (id: string, data: Partial<SkillSuggestion>) => void
}

export function SkillSuggestionCard({ suggestion, onAccept, onDismiss, onUpdate }: SkillSuggestionCardProps) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(suggestion.name)
  const [description, setDescription] = useState(suggestion.description)
  const [steps, setSteps] = useState(suggestion.suggestedSteps)

  const handleSave = () => {
    onUpdate(suggestion.id, { name, description, suggestedSteps: steps })
    setEditing(false)
  }

  const handleCancel = () => {
    setName(suggestion.name)
    setDescription(suggestion.description)
    setSteps(suggestion.suggestedSteps)
    setEditing(false)
  }

  const updateStep = (index: number, field: keyof SkillStep, value: string) => {
    setSteps(prev => prev.map((s, i) => i === index ? { ...s, [field]: value } : s))
  }

  const addStep = () => {
    setSteps(prev => [...prev, { title: '', description: '', stepType: 'action' as const, stepOrder: prev.length + 1 }])
  }

  const removeStep = (index: number) => {
    setSteps(prev => prev.filter((_, i) => i !== index).map((s, i) => ({ ...s, stepOrder: i + 1 })))
  }

  return (
    <div className="p-4 border border-gray-200 rounded-lg bg-white">
      {editing ? (
        <div className="space-y-3">
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">名称</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded-md text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">描述</label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              rows={2}
              className="w-full px-3 py-1.5 border border-gray-300 rounded-md text-sm resize-none"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">步骤</label>
            <div className="space-y-2">
              {steps.map((step, i) => (
                <div key={i} className="flex gap-2 items-start">
                  <span className="text-xs text-gray-400 mt-2 shrink-0">#{i + 1}</span>
                  <div className="flex-1 space-y-1">
                    <input
                      type="text"
                      value={step.title}
                      onChange={e => updateStep(i, 'title', e.target.value)}
                      placeholder="步骤标题"
                      className="w-full px-2 py-1 border border-gray-300 rounded text-sm"
                    />
                    <input
                      type="text"
                      value={step.description}
                      onChange={e => updateStep(i, 'description', e.target.value)}
                      placeholder="步骤描述"
                      className="w-full px-2 py-1 border border-gray-300 rounded text-sm"
                    />
                  </div>
                  <select
                    value={step.stepType}
                    onChange={e => updateStep(i, 'stepType', e.target.value)}
                    className="px-2 py-1 border border-gray-300 rounded text-xs mt-0.5"
                  >
                    <option value="action">action</option>
                    <option value="decision">decision</option>
                    <option value="validation">validation</option>
                    <option value="reference">reference</option>
                  </select>
                  <button
                    onClick={() => removeStep(i)}
                    className="text-red-400 hover:text-red-600 text-xs mt-1"
                  >
                    x
                  </button>
                </div>
              ))}
              <button onClick={addStep} className="text-xs text-primary-600 hover:underline">
                + 添加步骤
              </button>
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleSave} className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm">
              保存
            </button>
            <button onClick={handleCancel} className="px-3 py-1.5 border border-gray-300 rounded-md text-sm">
              取消
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="flex items-start justify-between mb-2">
            <div>
              <h3 className="font-semibold text-gray-900">{suggestion.name}</h3>
              {suggestion.category && (
                <span className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded">
                  {suggestion.category}
                </span>
              )}
            </div>
            <span className="text-xs text-gray-400">AI 推荐</span>
          </div>

          <p className="text-sm text-gray-600 mb-3">{suggestion.description}</p>

          <div className="mb-3">
            <p className="text-xs text-gray-400 mb-1">触发条件：{suggestion.triggerDescription}</p>
          </div>

          <div className="mb-3 space-y-1.5">
            {suggestion.suggestedSteps.map((step, i) => (
              <div key={i} className="flex items-start gap-2 text-sm">
                <span className="text-xs font-mono text-gray-400 shrink-0 mt-0.5">#{i + 1}</span>
                <span className="text-xs px-1 py-0.5 bg-gray-100 rounded shrink-0">{step.stepType}</span>
                <div>
                  <span className="font-medium text-gray-800">{step.title}</span>
                  <span className="text-gray-500 ml-1">— {step.description}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="mb-3 p-2 bg-gray-50 rounded text-xs text-gray-500">
            <span className="font-medium">来源：</span>{suggestion.sourceSummary}
          </div>

          <div className="flex gap-2">
            <button
              onClick={() => onAccept(suggestion.id)}
              className="px-3 py-1.5 bg-primary-600 text-white rounded-md text-sm hover:bg-primary-700"
            >
              采纳
            </button>
            <button
              onClick={() => setEditing(true)}
              className="px-3 py-1.5 border border-gray-300 rounded-md text-sm hover:bg-gray-50"
            >
              编辑
            </button>
            <button
              onClick={() => onDismiss(suggestion.id)}
              className="px-3 py-1.5 text-sm text-gray-500 hover:text-gray-700"
            >
              忽略
            </button>
          </div>
        </>
      )}
    </div>
  )
}
