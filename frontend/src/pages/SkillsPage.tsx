import { useState, useEffect } from 'react'
import { skillsApi } from '@/api/skills'
import { useSSE } from '@/hooks/useSSE'
import { useAuthStore } from '@/stores/authStore'
import type { Skill } from '@/types/api'

export function SkillsPage() {
  const { isAuthenticated } = useAuthStore()
  const [description, setDescription] = useState('')
  const [skills, setSkills] = useState<Skill[]>([])
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null)
  const { isStreaming, output, stream, reset } = useSSE()

  useEffect(() => {
    if (isAuthenticated) {
      skillsApi.getSkills().then(setSkills).catch(console.error)
    }
  }, [isAuthenticated])

  const handleExtract = async () => {
    if (!description.trim()) return
    reset()
    try {
      const generator = skillsApi.extract({ description })
      await stream(generator, {
        onDone: () => {
          if (isAuthenticated) {
            skillsApi.getSkills().then(setSkills).catch(console.error)
          }
        },
      })
    } catch (err) {
      console.error(err)
    }
  }

  const handleExport = async (id: string) => {
    try {
      const res = await skillsApi.exportSkill(id)
      const blob = new Blob([res.content], { type: 'text/markdown' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `skill-${id}.md`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-4">Skills 构建</h1>

      <div className="mb-6 space-y-3">
        <textarea
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="描述一个工作流... 例如：创建 React 组件，包含 TypeScript 类型、单元测试和 Storybook 故事"
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
        />
        <button
          onClick={handleExtract}
          disabled={isStreaming || !description.trim()}
          className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50 transition-colors"
        >
          {isStreaming ? '提取中...' : '提取 Skill'}
        </button>
      </div>

      {output && (
        <div className="mb-6">
          <h2 className="text-sm font-medium text-gray-500 mb-2">提取结果</h2>
          <pre className="p-4 bg-gray-900 text-gray-100 rounded-lg text-sm overflow-auto whitespace-pre-wrap max-h-96">
            {output}
          </pre>
        </div>
      )}

      {selectedSkill && (
        <div className="mb-6 p-4 border border-gray-200 rounded-lg">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-gray-900">{selectedSkill.name}</h2>
            <button
              onClick={() => handleExport(selectedSkill.id)}
              className="text-sm text-primary-600 hover:underline"
            >
              导出 .md
            </button>
          </div>
          <p className="text-sm text-gray-600 mb-3">{selectedSkill.description}</p>
          <div className="space-y-2">
            {selectedSkill.steps.map(step => (
              <div key={step.id} className="p-3 bg-gray-50 rounded">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-mono text-gray-400">#{step.stepOrder}</span>
                  <span className="text-xs px-1.5 py-0.5 bg-gray-200 rounded">{step.stepType}</span>
                  <span className="font-medium text-sm">{step.title}</span>
                </div>
                <p className="text-sm text-gray-600">{step.description}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {isAuthenticated && skills.length > 0 && (
        <div>
          <h2 className="text-sm font-medium text-gray-500 mb-3">我的 Skills</h2>
          <div className="space-y-2">
            {skills.map(skill => (
              <button
                key={skill.id}
                onClick={() => setSelectedSkill(skill)}
                className="w-full text-left p-3 border border-gray-200 rounded-lg hover:border-primary-300 transition-all"
              >
                <h3 className="font-medium text-sm text-gray-900">{skill.name}</h3>
                <p className="text-xs text-gray-500">{skill.description}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
