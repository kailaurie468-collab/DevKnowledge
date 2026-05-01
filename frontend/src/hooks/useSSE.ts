import { useState, useCallback, useRef } from 'react'

interface UseSSEOptions {
  onChunk?: (chunk: string) => void
  onDone?: () => void
  onError?: (error: Error) => void
}

export function useSSE() {
  const [isStreaming, setIsStreaming] = useState(false)
  const [output, setOutput] = useState('')
  const abortRef = useRef<AbortController | null>(null)

  const stream = useCallback(async (
    generator: AsyncGenerator<string>,
    options: UseSSEOptions = {}
  ) => {
    const { onChunk, onDone, onError } = options
    setIsStreaming(true)
    setOutput('')

    try {
      for await (const chunk of generator) {
        setOutput(prev => prev + chunk)
        onChunk?.(chunk)
      }
      onDone?.()
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err))
      onError?.(error)
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
  }, [])

  return { isStreaming, output, stream, cancel, reset }
}
