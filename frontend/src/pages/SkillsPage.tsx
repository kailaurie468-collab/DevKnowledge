import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { skillsApi } from '@/api/skills'
import { useSSE } from '@/hooks/useSSE'
import { useAuthStore } from '@/stores/authStore'
import { SkillSuggestionCard } from '@/components/skills/SkillSuggestionCard'
import type { Skill, SkillSuggestion } from '@/types/api'

type Tab = 'mine' | 'suggested'

export function SkillsPage() {
  const { isAuthenticated } = useAuthStore()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('mine')
  const [description, setDescription] = useState('')
  const [skills, setSkills] = useState<Skill[]>([])
  const [suggestions, setSuggestions] = useState<SkillSuggestion[]>([])
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const { isStreaming, output, stream, reset } = useSSE()

  useEffect(() => {
    if (isAuthenticated) {
      skillsApi.getSkills().then(setSkills).catch(console.error)
      skillsApi.getSuggestions().then(setSuggestions).catch(console.error)
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

  const handleRefreshSuggestions = async () => {
    setRefreshing(true)
    try {
      await skillsApi.refreshSuggestions()
      const data = await skillsApi.getSuggestions()
      setSuggestions(data)
    } catch (err) {
      console.error(err)
    } finally {
      setRefreshing(false)
    }
  }

  const handleAcceptSuggestion = async (id: string) => {
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    try {
      const skill = await skillsApi.acceptSuggestion(id)
      setSkills(prev => [skill, ...prev])
      setSuggestions(prev => prev.filter(s => s.id !== id))
    } catch (err) {
      console.error(err)
    }
  }

  const handleDismissSuggestion = async (id: string) => {
    if (!isAuthenticated) { navigate('/login'); return }
    try {
      await skillsApi.dismissSuggestion(id)
      setSuggestions(prev => prev.filter(s => s.id !== id))
    } catch (err) {
      console.error(err)
    }
  }

  const handleUpdateSuggestion = async (id: string, data: Partial<SkillSuggestion>) => {
    if (!isAuthenticated) { navigate('/login'); return }
    try {
      const updated = await skillsApi.updateSuggestion(id, data)
      setSuggestions(prev => prev.map(s => s.id === id ? updated : s))
    } catch (err) {
      console.error(err)
    }
  }

  const pendingSuggestions = suggestions.filter(s => s.status === 'pending')

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-4">Skills 构建</h1>

      {/* 提取区 */}
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

      {/* 提取结果 */}
      {output && (
        <div className="mb-6">
          <h2 className="text-sm font-medium text-gray-500 mb-2">提取结果</h2>
          <pre className="p-4 bg-gray-900 text-gray-100 rounded-lg text-sm overflow-auto whitespace-pre-wrap max-h-96">
            {output}
          </pre>
        </div>
      )}

      {/* 选中的 Skill 详情 */}
      {selectedSkill && (
        <div className="mb-6 p-4 border border-gray-200 rounded-lg">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-gray-900">{selectedSkill.name}</h2>
            <div className="flex gap-3">
              <button
                onClick={() => handleExport(selectedSkill.id)}
                className="text-sm text-primary-600 hover:underline"
              >
                导出 .md
              </button>
              <button
                onClick={() => setSelectedSkill(null)}
                className="text-sm text-gray-400 hover:text-gray-600"
              >
                关闭
              </button>
            </div>
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

      {/* Tab 切换 */}
      <div>
        <div className="flex gap-1 mb-4 border-b border-gray-200">
          <button
            onClick={() => setTab('mine')}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === 'mine'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            我的 Skills {isAuthenticated && `(${skills.length})`}
          </button>
          <button
            onClick={() => setTab('suggested')}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              tab === 'suggested'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            推荐
            {pendingSuggestions.length > 0 && (
              <span className="ml-1.5 px-1.5 py-0.5 bg-primary-100 text-primary-700 text-xs rounded-full">
                {pendingSuggestions.length}
              </span>
            )}
          </button>
        </div>

        {/* 我的 Skills */}
        {tab === 'mine' && (
          <div className="space-y-2">
            {!isAuthenticated ? (
              <div className="text-center py-8">
                <p className="text-sm text-gray-500 mb-2">登录后可保存和管理你的 Skills</p>
                <button
                  onClick={() => navigate('/login')}
                  className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700"
                >
                  去登录
                </button>
              </div>
            ) : (
              <>
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
                {skills.length === 0 && (
                  <p className="text-gray-500 text-sm">暂无 Skills，在上方输入描述开始提取。</p>
                )}
              </>
            )}
          </div>
        )}

        {/* 推荐 */}
        {tab === 'suggested' && (
          <div>
            <div className="flex items-center justify-between mb-4">
              <p className="text-sm text-gray-500">
                基于你的使用习惯，AI 推荐以下工作流。
                {!isAuthenticated && (
                  <button onClick={() => navigate('/login')} className="ml-2 text-primary-600 hover:underline">
                    登录
                  </button>
                )}
                {!isAuthenticated && <span className="text-gray-400">后可采纳</span>}
              </p>
              <button
                onClick={handleRefreshSuggestions}
                disabled={refreshing}
                className="text-sm text-primary-600 hover:underline disabled:opacity-50"
              >
                {refreshing ? '刷新中...' : '刷新推荐'}
              </button>
            </div>
            <div className="space-y-3">
              {pendingSuggestions.map(suggestion => (
                <SkillSuggestionCard
                  key={suggestion.id}
                  suggestion={suggestion}
                  onAccept={handleAcceptSuggestion}
                  onDismiss={handleDismissSuggestion}
                  onUpdate={handleUpdateSuggestion}
                />
              ))}
              {pendingSuggestions.length === 0 && (
                <div className="text-center py-8 text-gray-400">
                  <p className="text-sm">暂无推荐</p>
                  <p className="text-xs mt-1">持续使用平台后，系统会自动分析你的使用模式并推荐 Skills。</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
