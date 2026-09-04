import { useState, useEffect, type ReactNode } from 'react'
import { demosApi } from '@/api/demos'
import { kbApi } from '@/api/kb'
import { settingsApi } from '@/api/settings'
import { embeddingApi } from '@/api/embedding'
import { useSSE } from '@/hooks/useSSE'
import { useAuthStore } from '@/stores/authStore'
import { useNotify } from '@/stores/notify'
import { CustomSelect } from '@/components/effects/CustomSelect'
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
    <div className="relative group rounded-lg overflow-hidden border border-gray-200 dark:border-gray-700">
      {lang && (
        <div className="flex items-center justify-between px-4 py-1.5 bg-gray-100 dark:bg-gray-800 text-xs text-gray-500 dark:text-gray-400 font-mono border-b border-gray-200 dark:border-gray-700">
          <span>{lang}</span>
          <button onClick={handleCopy} className="opacity-0 group-hover:opacity-100 transition-opacity text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            {copied ? '已复制' : '复制'}
          </button>
        </div>
      )}
      <pre className="p-4 bg-gray-50 dark:bg-gray-900 text-gray-800 dark:text-gray-200 text-sm font-mono leading-relaxed overflow-auto whitespace-pre-wrap break-words">
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
  const [language, setLanguage] = useState('')
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
  const [topK, setTopK] = useState(3)
  const [retrievalSource, setRetrievalSource] = useState<'rag' | 'wiki' | 'none'>('none')
  const [ragChunkCounts, setRagChunkCounts] = useState<Record<string, number>>({})
  const [hasEmbeddingConfig, setHasEmbeddingConfig] = useState(true)
  const { isStreaming, output, events, stream, reset } = useSSE()

  const fetchDemos = (page = 1, keyword = demoKeyword) => {
    demosApi.getDemos({ page, size: 6, keyword: keyword || undefined })
      .then(res => {
        setDemos(res.records)
        setDemoTotalPages(res.pages)
        setDemoTotal(res.total)
        setDemoPage(page)
        // 获取 RAG 指标，构建 demoId → chunkCount 映射
        settingsApi.getRagMetrics().then(metrics => {
          const map: Record<string, number> = {}
          metrics.forEach(m => { if (m.ragUsed) map[m.demoId] = m.chunkCount })
          setRagChunkCounts(map)
        }).catch(() => {})
      })
      .catch(console.error)
  }

  useEffect(() => {
    knowledgeApi.getFrameworks().then(setFrameworks).catch(console.error)
    if (isAuthenticated) {
      fetchDemos(1)
      kbApi.getKbs().then(setKbs).catch(console.error)
      embeddingApi.getAllConfigs().then(list => {
        setHasEmbeddingConfig(list.length > 0)
      }).catch(() => {})
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
          kbId: retrievalSource === 'rag' ? selectedKbId || undefined : undefined,
          topK: retrievalSource === 'rag' && selectedKbId ? topK : undefined,
          retrievalSource: retrievalSource,
        })(signal),
      {
        onChunk: (chunk) => {
          if (chunk.event === 'error' || chunk.data.startsWith('[ERROR]')) {
            notify(chunk.data.replace('[ERROR]', ''), 'error')
          }
          if (chunk.event === 'warning') {
            notify(chunk.data, 'warning')
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
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">Demo 生成</h1>

      <div className="mb-6 space-y-3">
        {/* 输入区 */}
        <textarea
          data-tour="demo-prompt"
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
          placeholder="描述你想要的代码... 例如 React useEffect 发起 API 请求并处理加载状态"
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
        />

        {/* 第一行：基础选项 + 生成按钮 */}
        <div className="flex items-center gap-3">
          <CustomSelect
            value={language}
            onChange={setLanguage}
            placeholder="选择语言..."
            options={[
              { value: 'typescript', label: 'TypeScript' },
              { value: 'javascript', label: 'JavaScript' },
              { value: 'java', label: 'Java' },
              { value: 'kotlin', label: 'Kotlin' },
              { value: 'python', label: 'Python' },
            ]}
            className="w-40"
          />
          <CustomSelect
            value={frameworkId}
            onChange={setFrameworkId}
            placeholder="选择框架（可选）"
            options={[
              { value: '', label: '不选择框架' },
              ...frameworks.map(fw => ({ value: fw.id, label: fw.name })),
            ]}
            className="w-48"
          />

          <div className="flex-1" />

          <button
            onClick={handleGenerate}
            disabled={isStreaming || !prompt.trim() || !language || (retrievalSource === 'rag' && !selectedKbId)}
            className="px-5 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700 disabled:opacity-50 transition-colors"
          >
            {isStreaming ? '生成中...' : '生成'}
          </button>
        </div>

        {/* 第二行：知识检索配置 */}
        <div className="bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 rounded-lg p-3 space-y-3">
          {/* 检索源切换 */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500 dark:text-gray-400 font-medium uppercase tracking-wide">知识检索</span>
            <div className="flex rounded-md overflow-hidden border border-gray-300 dark:border-gray-600">
              {([
                { value: 'none' as const, label: '不使用' },
                { value: 'rag' as const, label: 'RAG 向量' },
                { value: 'wiki' as const, label: 'Wiki 图谱' },
              ]).map(source => (
                <button
                  key={source.value}
                  onClick={() => {
                    setRetrievalSource(source.value)
                    if (source.value !== 'rag') setSelectedKbId('')
                  }}
                  className={`px-3 py-1.5 text-xs font-medium transition-colors ${
                    retrievalSource === source.value
                      ? 'bg-primary-600 text-white'
                      : 'bg-white dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-600'
                  }`}
                >
                  {source.label}
                </button>
              ))}
            </div>
          </div>

          {/* RAG 配置区 */}
          {retrievalSource === 'rag' && (
            <div className="space-y-2">
              {/* Embedding 未配置警告 */}
              {!hasEmbeddingConfig && (
                <div className="flex items-start gap-2 p-2 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded text-xs">
                  <svg className="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
                  </svg>
                  <span className="text-amber-700 dark:text-amber-300">
                    未配置 Embedding AI，当前使用关键词搜索，检索效果可能较差。建议在设置页配置 Embedding 以获得更好的语义检索效果。
                  </span>
                </div>
              )}

              <div className="flex items-center gap-4">
                <div className="flex-1">
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">知识库</label>
                  <CustomSelect
                    value={selectedKbId}
                    onChange={setSelectedKbId}
                    placeholder={kbs.length > 0 ? '选择知识库...' : '暂无知识库'}
                    disabled={kbs.length === 0}
                    options={kbs.map(kb => ({ value: kb.id, label: kb.name }))}
                  />
                </div>

                {selectedKbId && (
                  <div className="w-48">
                    <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">
                      检索数量 <span className="font-medium text-gray-700 dark:text-gray-300">{topK}</span>
                    </label>
                    <input
                      type="range"
                      min={1}
                      max={10}
                      value={topK}
                      onChange={e => setTopK(Number(e.target.value))}
                      className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-600"
                    />
                  </div>
                )}

                {retrievalSource === 'rag' && !selectedKbId && (
                  <span className="text-xs text-amber-600">请选择知识库</span>
                )}
              </div>
            </div>
          )}

          {/* Wiki 提示 */}
          {retrievalSource === 'wiki' && (
            <div className="text-xs text-purple-600 dark:text-purple-400 bg-purple-50 dark:bg-purple-900/20 border border-purple-100 dark:border-purple-800 rounded px-3 py-2">
              将从 Wiki 知识图谱中检索相关页面作为上下文注入
            </div>
          )}
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
                <div key={i} className="p-3 bg-blue-50 dark:bg-blue-900/20 border-l-4 border-blue-400 rounded-r text-sm text-blue-800 dark:text-blue-300">
                  <span className="font-medium">思考：</span>{evt.data}
                </div>
              )
            }
            if (evt.event === 'tool_call') {
              const [name, ...rest] = evt.data.split(':')
              return (
                <div key={i} className="p-3 bg-amber-50 dark:bg-amber-900/20 border-l-4 border-amber-400 rounded-r text-sm text-amber-800 dark:text-amber-300">
                  <span className="font-medium">调用工具：</span>{name}
                  {rest.length > 0 && <span className="text-amber-600 dark:text-amber-400 ml-2">({rest.join(':')})</span>}
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
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400">输出</h2>
              {isStreaming && !selectedDemo && (
                <span className="flex items-center gap-1 text-xs text-primary-500">
                  <svg className="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  生成中...
                </span>
              )}
              {!isStreaming && !selectedDemo && output && (
                <span className="text-xs text-green-500">✓ 已完成</span>
              )}
            </div>
            {/* 流式中隐藏关闭按钮，完成后或查看历史时显示 */}
            {(selectedDemo || !isStreaming) && (
              <button
                onClick={() => {
                  setSelectedDemo(null)
                  if (!selectedDemo) reset()
                }}
                className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              >
                × 关闭
              </button>
            )}
          </div>
          <MarkdownOutput content={selectedDemo ? [selectedDemo.explanation, selectedDemo.codeContent ? '```' + selectedDemo.language + '\n' + selectedDemo.codeContent + '\n```' : ''].filter(Boolean).join('\n\n---\n\n') : output} />
        </div>
      )}

      {/* 历史记录 */}
      {isAuthenticated && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400">历史记录 {demoTotal > 0 && `(${demoTotal})`}</h2>
            <div className="flex gap-2">
              <input
                type="text"
                value={demoKeyword}
                onChange={e => setDemoKeyword(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') fetchDemos(1, demoKeyword) }}
                placeholder="搜索标题、描述..."
                className="px-2 py-1 border border-gray-300 dark:border-gray-600 rounded text-xs w-40 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
              <button
                onClick={() => fetchDemos(1, demoKeyword)}
                className="px-2 py-1 text-xs text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded transition-colors"
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
                      className="flex-1 text-left p-3 border border-gray-200 dark:border-gray-700 rounded-lg hover:border-primary-300 dark:hover:border-primary-500 transition-all bg-white dark:bg-gray-800"
                    >
                      <h3 className="font-medium text-sm text-gray-900 dark:text-gray-100">{demo.title}</h3>
                      <div className="flex items-center gap-2 mt-1">
                        <p className="text-xs text-gray-500 dark:text-gray-400 truncate flex-1">{demo.prompt}</p>
                        <span className="text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                          RAG: {ragChunkCounts[demo.id] ?? 0} 篇
                        </span>
                      </div>
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
                    className="px-2 py-1 text-xs border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed text-gray-700 dark:text-gray-300"
                  >
                    上一页
                  </button>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{demoPage} / {demoTotalPages}</span>
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
