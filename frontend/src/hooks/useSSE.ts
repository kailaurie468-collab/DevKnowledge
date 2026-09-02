import { useCallback, useRef } from 'react'
import { useDemoStore } from '@/stores/demoStore'
import { reportClientError } from '@/utils/errorReporting'

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
 * 使用全局 store 保持状态，切换页面后内容不丢失
 */
export function useSSE() {
  const { isStreaming, output, events, setIsStreaming, appendOutput, addEvent, reset } = useDemoStore()
  const abortRef = useRef<AbortController | null>(null)

  const stream = useCallback(async (
    generatorFn: GeneratorFn,
    options: UseSSEOptions = {}
  ) => {
    const { onChunk, onDone, onError } = options
    const controller = new AbortController()
    abortRef.current = controller
    reset()
    setIsStreaming(true)

    try {
      for await (const chunk of generatorFn(controller.signal)) {
        if (chunk.event === 'text' || chunk.event === 'message') {
          appendOutput(chunk.data)
        }
        addEvent(chunk)
        onChunk?.(chunk)
      }
      onDone?.()
    } catch (err) {
      if (controller.signal.aborted) return
      const error = err instanceof Error ? err : new Error(String(err))
      reportClientError({
        errorSummary: error.message,
        errorType: 'SSETransportError',
        stage: 'sse',
      })
      onError?.(error)
      throw error
    } finally {
      if (abortRef.current === controller) {
        setIsStreaming(false)
        abortRef.current = null
      }
    }
  }, [setIsStreaming, reset, appendOutput, addEvent])

  const cancel = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  return { isStreaming, output, events, stream, cancel, reset }
}
