import { useState, useEffect, type ReactNode } from 'react'
import { demosApi } from '@/api/demos'
import { kbApi } from '@/api/kb'
import { useSSE } from '@/hooks/useSSE'
import { useAuthStore } from '@/stores/authStore'
import { useNotify } from '@/stores/notify'
import type { Demo, Framework, KnowledgeBase } from '@/types/api'
import { knowledgeApi } from '@/api/knowledge'

/** Inline Markdown 格式化：加粗、行内代码、斜体、链接 */
function formatInline(text: string): ReactNode[] {
  const parts: ReactNode[] = []
  // 匹配顺序：行内代码 > 加粗 > 链接 > 斜体
  const regex = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\[([^\]]+)\]\(([^)]+)\))|(\*[^*]+\*)/g
  let last = 0
  let match: RegExpExecArray | null
  let key = 0

  while ((match = regex.exec(text)) !== null) {
    if (match.index > last) {
      parts.push(text.slice(last, match.index))
    }
    if (match[1]) {
      // 行内代码
      parts.push(<code key={key++} className="px-1.5 py-0.5 bg-gray-100 text-red-600 rounded text-xs font-mono">{match[1].slice(1, -1)}</code>)
    } else if (match[2]) {
      // 加粗
      parts.push(<strong key={key++} className="font-semibold text-gray-900">{match[2].slice(2, -2)}</strong>)
    } else if (match[3]) {
      // 链接
      parts.push(<a key={key++} href={match[5]} target="_blank" rel="noopener noreferrer" className="text-primary-600 hover:underline">{match[4]}</a>)
    } else if (match[6]) {
      // 斜体
      parts.push(<em key={key++} className="italic text-gray-600">{match[6].slice(1, -1)}</em>)
    }
    last = match.index + match[0].length
  }
  if (last < text.length) {
    parts.push(text.slice(last))
  }
  return parts
}

/** 代码块组件（带语言标签 + 复制按钮） */
function CodeBlock({ lang, code }: { lang: string; code: string }) {
  const [copied, setCopied] = useState(false)
  const handleCopy = () => {
    navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return (
    <div className="relative group">
      {lang && (
        <div className="flex items-center justify-between px-4 py-1.5 bg-gray-800 text-xs text-gray-400 font-mono">
          <span>{lang}</span>
          <button onClick={handleCopy} className="opacity-0 group-hover:opacity-100 transition-opacity text-gray-400 hover:text-white">
            {copied ? '已复制' : '复制'}
          </button>
        </div>
      )}
      <pre className="p-4 bg-gray-900 text-gray-100 text-sm font-mono leading-relaxed overflow-auto whitespace-pre max-h-80">
        {code}
      </pre>
    </div>
  )
}

/** Markdown 文本块渲染 */
function MarkdownText({ text }: { text: string }) {
  return (
    <div className="px-5 py-4 space-y-1">
      {text.split('\n').map((line, j) => {
        if (line.startsWith('# ')) return <h3 key={j} className="text-lg font-bold text-gray-900 mt-3 mb-1">{formatInline(line.slice(2))}</h3>
        if (line.startsWith('## ')) return <h4 key={j} className="text-base font-semibold text-gray-800 mt-2 mb-1">{formatInline(line.slice(3))}</h4>
        if (line.startsWith('### ')) return <h5 key={j} className="text-sm font-semibold text-gray-700 mt-2 mb-0.5">{formatInline(line.slice(4))}</h5>
        if (line.startsWith('- ') || line.startsWith('* ')) return <div key={j} className="flex gap-2 text-gray-700 pl-2"><span className="text-gray-400 select-none">-</span><span>{formatInline(line.slice(2))}</span></div>
        if (/^\d+\.\s/.test(line)) return <div key={j} className="flex gap-2 text-gray-700 pl-2"><span className="text-gray-400 select-none min-w-[1.5rem]">{line.match(/^\d+/)![0]}.</span><span>{formatInline(line.replace(/^\d+\.\s/, ''))}</span></div>
        if (line.trim() === '---') return <hr key={j} className="my-3 border-gray-200" />
        if (line.trim() === '') return <div key={j} className="h-2" />
        return <p key={j} className="text-gray-700 leading-relaxed">{formatInline(line)}</p>
      })}
    </div>
  )
}

/** 渲染完整的 Markdown 输出（代码块 + 文本块） */
function MarkdownOutput({ content }: { content: string }) {
  const blocks = content.split(/(```[\s\S]*?```)/g)
  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      {blocks.map((block, i) => {
        if (block.startsWith('```')) {
          const code = block.replace(/^```\w*\n?/, '').replace(/\n?```$/, '')
          const lang = block.match(/^```(\w+)/)?.[1] || ''
          return <CodeBlock key={i} lang={lang} code={code} />
        }
        if (block.trim() === '') return null
        return <MarkdownText key={i} text={block} />
      })}
    </div>
  )
}

export function DemoPage() {
  const { isAuthenticated } = useAuthStore()
  const { notify } = useNotify()
  const [prompt, setPrompt] = useState('')
  const [language, setLanguage] = useState('typescript')
  const [frameworkId, setFrameworkId] = useState('')
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [demos, setDemos] = useState<Demo[]>([])
  const [selectedDemo, setSelectedDemo] = useState<Demo | null>(null)
  const [demoPage, setDemoPage] = useState(1)
  const [demoTotalPages, setDemoTotalPages] = useState(1)
  const [demoTotal, setDemoTotal] = useState(0)
  const [demoKeyword, setDemoKeyword] = useState('')
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [selectedKbId, setSelectedKbId] = useState('')
  const { isStreaming, output, events, stream, reset } = useSSE()

  const fetchDemos = (page = 1, keyword = demoKeyword) => {
    demosApi.getDemos({ page, size: 6, keyword: keyword || undefined })
      .then(res => {
        setDemos(res.records)
        setDemoTotalPages(res.pages)
        setDemoTotal(res.total)
        setDemoPage(page)
      })
      .catch(console.error)
  }

  useEffect(() => {
    knowledgeApi.getFrameworks().then(setFrameworks).catch(console.error)
    if (isAuthenticated) {
      fetchDemos(1)
      kbApi.getKbs().then(setKbs).catch(console.error)
    }
  }, [isAuthenticated])

  const handleGenerate = async () => {
    if (!prompt.trim()) return
    reset()
    setSelectedDemo(null)
    try {
      await stream(
        (signal) => demosApi.generate({
          prompt,
          language,
          frameworkId: frameworkId || undefined,
          kbId: selectedKbId || undefined,
        })(signal),
      {
        onChunk: (chunk) => {
          if (chunk.event === 'error' || chunk.data.startsWith('[ERROR]')) {
            notify(chunk.data.replace('[ERROR]', ''), 'error')
          }
        },
        onDone: () => {
          if (isAuthenticated) fetchDemos(1)
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
          <select
            value={selectedKbId}
            onChange={e => setSelectedKbId(e.target.value)}
            disabled={kbs.length === 0}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            title={kbs.length === 0 ? '暂无知识库，请先在知识库页面创建' : ''}
          >
            <option value="">{kbs.length > 0 ? '不使用知识库' : '暂无知识库'}</option>
            {kbs.map(kb => (
              <option key={kb.id} value={kb.id}>{kb.name}</option>
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
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-sm font-medium text-gray-500">输出</h2>
            <button
              onClick={() => {
                setSelectedDemo(null)
                if (!selectedDemo) reset()
              }}
              className="text-xs text-gray-400 hover:text-gray-600 transition-colors"
            >
              × 关闭
            </button>
          </div>
          <MarkdownOutput content={selectedDemo ? '```' + selectedDemo.language + '\n' + selectedDemo.codeContent + '\n```\n\n---\n\n' + selectedDemo.explanation : output} />
        </div>
      )}

      {/* 历史记录 */}
      {isAuthenticated && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-medium text-gray-500">历史记录 {demoTotal > 0 && `(${demoTotal})`}</h2>
            <div className="flex gap-2">
              <input
                type="text"
                value={demoKeyword}
                onChange={e => setDemoKeyword(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') fetchDemos(1, demoKeyword) }}
                placeholder="搜索标题、描述..."
                className="px-2 py-1 border border-gray-300 rounded text-xs w-40 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
              <button
                onClick={() => fetchDemos(1, demoKeyword)}
                className="px-2 py-1 text-xs text-primary-600 hover:bg-primary-50 rounded transition-colors"
              >
                搜索
              </button>
            </div>
          </div>

          {demos.length > 0 ? (
            <>
              <div className="space-y-2">
                {demos.map(demo => (
                  <div key={demo.id} className="flex items-stretch gap-2">
                    <button
                      onClick={() => setSelectedDemo(demo)}
                      className="flex-1 text-left p-3 border border-gray-200 rounded-lg hover:border-primary-300 transition-all"
                    >
                      <h3 className="font-medium text-sm text-gray-900">{demo.title}</h3>
                      <p className="text-xs text-gray-500 truncate">{demo.prompt}</p>
                    </button>
                    <button
                      onClick={async () => {
                        if (!confirm('确定删除此 Demo？')) return
                        try {
                          await demosApi.deleteDemo(demo.id)
                          setDemos(prev => prev.filter(d => d.id !== demo.id))
                          setDemoTotal(prev => prev - 1)
                          if (selectedDemo?.id === demo.id) setSelectedDemo(null)
                          notify('已删除', 'success')
                        } catch (err) {
                          notify(err instanceof Error ? err.message : '删除失败', 'error')
                        }
                      }}
                      className="px-2 text-gray-400 hover:text-red-500 transition-colors"
                      title="删除"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                ))}
              </div>

              {/* 分页 */}
              {demoTotalPages > 1 && (
                <div className="flex items-center justify-center gap-2 mt-3">
                  <button
                    onClick={() => fetchDemos(demoPage - 1)}
                    disabled={demoPage <= 1}
                    className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    上一页
                  </button>
                  <span className="text-xs text-gray-500">{demoPage} / {demoTotalPages}</span>
                  <button
                    onClick={() => fetchDemos(demoPage + 1)}
                    disabled={demoPage >= demoTotalPages}
                    className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    下一页
                  </button>
                </div>
              )}
            </>
          ) : (
            <p className="text-gray-400 text-sm">暂无记录</p>
          )}
        </div>
      )}
    </div>
  )
}
