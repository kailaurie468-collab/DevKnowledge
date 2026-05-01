import { useState, useEffect } from 'react'
import { demosApi } from '@/api/demos'
import { useSSE } from '@/hooks/useSSE'
import { useAuthStore } from '@/stores/authStore'
import type { Demo, Framework } from '@/types/api'
import { knowledgeApi } from '@/api/knowledge'

export function DemoPage() {
  const { isAuthenticated } = useAuthStore()
  const [prompt, setPrompt] = useState('')
  const [language, setLanguage] = useState('typescript')
  const [frameworkId, setFrameworkId] = useState('')
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [demos, setDemos] = useState<Demo[]>([])
  const [selectedDemo, setSelectedDemo] = useState<Demo | null>(null)
  const { isStreaming, output, stream, reset } = useSSE()

  useEffect(() => {
    knowledgeApi.getFrameworks().then(setFrameworks).catch(console.error)
    if (isAuthenticated) {
      demosApi.getDemos().then(setDemos).catch(console.error)
    }
  }, [isAuthenticated])

  const handleGenerate = async () => {
    if (!prompt.trim()) return
    reset()
    setSelectedDemo(null)
    try {
      const generator = demosApi.generate({
        prompt,
        language,
        frameworkId: frameworkId || undefined,
      })
      await stream(generator, {
        onDone: () => {
          if (isAuthenticated) {
            demosApi.getDemos().then(setDemos).catch(console.error)
          }
        },
      })
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-4">Demo 生成</h1>

      <div className="mb-6 space-y-3">
        <textarea
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
          placeholder="描述你想要的代码... 例如 React useEffect 发起 API 请求并处理加载状态"
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
        />
        <div className="flex gap-3">
          <select
            value={language}
            onChange={e => setLanguage(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm"
          >
            <option value="typescript">TypeScript</option>
            <option value="javascript">JavaScript</option>
            <option value="java">Java</option>
            <option value="kotlin">Kotlin</option>
            <option value="python">Python</option>
          </select>
          <select
            value={frameworkId}
            onChange={e => setFrameworkId(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm"
          >
            <option value="">选择框架（可选）</option>
            {frameworks.map(fw => (
              <option key={fw.id} value={fw.id}>{fw.name}</option>
            ))}
          </select>
          <button
            onClick={handleGenerate}
            disabled={isStreaming || !prompt.trim()}
            className="px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50 transition-colors"
          >
            {isStreaming ? '生成中...' : '生成'}
          </button>
        </div>
      </div>

      {(output || selectedDemo) && (
        <div className="mb-6">
          <h2 className="text-sm font-medium text-gray-500 mb-2">输出</h2>
          <pre className="p-4 bg-gray-900 text-gray-100 rounded-lg text-sm overflow-auto whitespace-pre-wrap max-h-96">
            {selectedDemo ? selectedDemo.codeContent + '\n\n---\n\n' + selectedDemo.explanation : output}
          </pre>
        </div>
      )}

      {isAuthenticated && demos.length > 0 && (
        <div>
          <h2 className="text-sm font-medium text-gray-500 mb-3">历史记录</h2>
          <div className="space-y-2">
            {demos.map(demo => (
              <button
                key={demo.id}
                onClick={() => setSelectedDemo(demo)}
                className="w-full text-left p-3 border border-gray-200 rounded-lg hover:border-primary-300 transition-all"
              >
                <h3 className="font-medium text-sm text-gray-900">{demo.title}</h3>
                <p className="text-xs text-gray-500 truncate">{demo.prompt}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
