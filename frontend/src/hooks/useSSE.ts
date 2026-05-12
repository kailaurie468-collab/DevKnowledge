import { useState, useCallback, useRef, useEffect } from 'react'

export interface SSEChunk {
  event: string
  data: string
}

interface UseSSEOptions {
  onChunk?: (chunk: SSEChunk) => void
  onDone?: () => void
  onError?: (error: Error) => void
}

type GeneratorFn = (signal: AbortSignal) => AsyncGenerator<SSEChunk>

/**
 * SSE 流式 Hook
 * 支持 ReAct 事件类型：thought / tool_call / tool_result / text / done / error
 */
export function useSSE() {
  const [isStreaming, setIsStreaming] = useState(false)
  const [output, setOutput] = useState('')
  const [events, setEvents] = useState<SSEChunk[]>([])
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    return () => { abortRef.current?.abort() }
  }, [])

  const stream = useCallback(async (
    generatorFn: GeneratorFn,
    options: UseSSEOptions = {}
  ) => {
    const { onChunk, onDone, onError } = options
    const controller = new AbortController()
    abortRef.current = controller
    setIsStreaming(true)
    setOutput('')
    setEvents([])

    try {
      for await (const chunk of generatorFn(controller.signal)) {
        if (chunk.event === 'text' || chunk.event === 'message') {
          setOutput(prev => prev + chunk.data)
        }
        setEvents(prev => [...prev, chunk])
        onChunk?.(chunk)
      }
      onDone?.()
    } catch (err) {
      if (controller.signal.aborted) return
      const error = err instanceof Error ? err : new Error(String(err))
      onError?.(error)
      throw error
    } finally {
      if (abortRef.current === controller) {
        setIsStreaming(false)
        abortRef.current = null
      }
    }
  }, [])

  const cancel = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  const reset = useCallback(() => {
    setOutput('')
    setEvents([])
  }, [])

  return { isStreaming, output, events, stream, cancel, reset }
}
