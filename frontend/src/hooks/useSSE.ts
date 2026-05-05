import { useState, useCallback, useRef } from 'react'

export interface SSEChunk {
  event: string
  data: string
}

interface UseSSEOptions {
  onChunk?: (chunk: SSEChunk) => void
  onDone?: () => void
  onError?: (error: Error) => void
}

/**
 * SSE 流式 Hook
 * 支持 ReAct 事件类型：thought / tool_call / tool_result / text / done / error
 */
export function useSSE() {
  const [isStreaming, setIsStreaming] = useState(false)
  const [output, setOutput] = useState('')
  const [events, setEvents] = useState<SSEChunk[]>([])
  const abortRef = useRef<AbortController | null>(null)

  const stream = useCallback(async (
    generator: AsyncGenerator<SSEChunk>,
    options: UseSSEOptions = {}
  ) => {
    const { onChunk, onDone, onError } = options
    setIsStreaming(true)
    setOutput('')
    setEvents([])

    try {
      for await (const chunk of generator) {
        // 累积纯文本输出（兼容旧用法）
        if (chunk.event === 'text' || chunk.event === 'message') {
          setOutput(prev => prev + chunk.data)
        }
        // 记录所有事件
        setEvents(prev => [...prev, chunk])
        onChunk?.(chunk)
      }
      onDone?.()
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err))
      onError?.(error)
      throw error
    } finally {
      setIsStreaming(false)
    }
  }, [])

  const cancel = useCallback(() => {
    abortRef.current?.abort()
    setIsStreaming(false)
  }, [])

  const reset = useCallback(() => {
    setOutput('')
    setEvents([])
  }, [])

  return { isStreaming, output, events, stream, cancel, reset }
}
