import { useState, useEffect } from 'react'
import { demosApi } from '@/api/demos'
import { useSSE } from '@/hooks/useSSE'
import { useAuthStore } from '@/stores/authStore'
import { useNotify } from '@/stores/notify'
import type { Demo, Framework } from '@/types/api'
import { knowledgeApi } from '@/api/knowledge'

export function DemoPage() {
  const { isAuthenticated } = useAuthStore()
  const { notify } = useNotify()
  const [prompt, setPrompt] = useState('')
  const [language, setLanguage] = useState('typescript')
  const [frameworkId, setFrameworkId] = useState('')
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [demos, setDemos] = useState<Demo[]>([])
  const [selectedDemo, setSelectedDemo] = useState<Demo | null>(null)
  const { isStreaming, output, events, stream, reset } = useSSE()

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
        onChunk: (chunk) => {
          if (chunk.event === 'error' || chunk.data.startsWith('[ERROR]')) {
            notify(chunk.data.replace('[ERROR]', ''), 'error')
          }
        },
        onDone: () => {
          if (isAuthenticated) {
            demosApi.getDemos().then(setDemos).catch(console.error)
          }
        },
      })
    } catch (err) {
      notify(err instanceof Error ? err.message : '生成失败，请检查网络或 AI 配置', 'error')
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

      {/* ReAct 推理过程展示 */}
      {events.length > 0 && (
        <div className="mb-6 space-y-2">
          {events.map((evt, i) => {
            if (evt.event === 'text' || evt.event === 'message') {
              return null
            }
            if (evt.event === 'thought') {
              return (
                <div key={i} className="p-3 bg-blue-50 border-l-4 border-blue-400 rounded-r text-sm text-blue-800">
                  <span className="font-medium">思考：</span>{evt.data}
                </div>
              )
            }
            if (evt.event === 'tool_call') {
              const [name, ...rest] = evt.data.split(':')
              return (
                <div key={i} className="p-3 bg-amber-50 border-l-4 border-amber-400 rounded-r text-sm text-amber-800">
                  <span className="font-medium">调用工具：</span>{name}
                  {rest.length > 0 && <span className="text-amber-600 ml-2">({rest.join(':')})</span>}
                </div>
              )
            }
            return null
          })}
        </div>
      )}

      {/* 代码输出 */}
      {(output || selectedDemo) && (
        <div className="mb-6">
          <h2 className="text-sm font-medium text-gray-500 mb-2">输出</h2>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            {(selectedDemo ? selectedDemo.codeContent + '\n\n---\n\n' + selectedDemo.explanation : output)
              .split(/(```[\s\S]*?```)/g)
              .map((block, i) => {
                if (block.startsWith('```')) {
                  const code = block.replace(/^```\w*\n?/, '').replace(/\n?```$/, '')
                  const lang = block.match(/^```(\w+)/)?.[1] || ''
                  return (
                    <div key={i}>
                      {lang && (
                        <div className="px-4 py-1.5 bg-gray-800 text-xs text-gray-400 font-mono">
                          {lang}
                        </div>
                      )}
                      <pre className="p-4 bg-gray-900 text-gray-100 text-sm font-mono leading-relaxed overflow-auto whitespace-pre max-h-80">
                        {code}
                      </pre>
                    </div>
                  )
                }
                // 普通文本
                return (
                  <div key={i} className="px-5 py-4 space-y-2">
                    {block.split('\n').map((line, j) => {
                      if (line.startsWith('# ')) return <h3 key={`${i}-${j}`} className="text-lg font-bold text-gray-900 mt-3 mb-1">{line.slice(2)}</h3>
                      if (line.startsWith('## ')) return <h4 key={`${i}-${j}`} className="text-base font-semibold text-gray-800 mt-2 mb-1">{line.slice(3)}</h4>
                      if (line.startsWith('### ')) return <h5 key={`${i}-${j}`} className="text-sm font-semibold text-gray-700 mt-2 mb-0.5">{line.slice(4)}</h5>
                      if (line.startsWith('- ')) return <div key={`${i}-${j}`} className="flex gap-2 text-gray-700"><span className="text-gray-400">-</span><span>{line.slice(2)}</span></div>
                      if (line.trim() === '---') return <hr key={`${i}-${j}`} className="my-2 border-gray-200" />
                      if (line.trim() === '') return <div key={`${i}-${j}`} className="h-1" />
                      return <p key={`${i}-${j}`} className="text-gray-700 leading-relaxed">{line}</p>
                    })}
                  </div>
                )
              })}
          </div>
        </div>
      )}

      {/* 历史记录 */}
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
