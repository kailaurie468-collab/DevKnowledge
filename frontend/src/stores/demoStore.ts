import { create } from 'zustand'
import type { SSEChunk } from '@/hooks/useSSE'

interface DemoState {
  isStreaming: boolean
  output: string
  events: SSEChunk[]
  setIsStreaming: (v: boolean) => void
  appendOutput: (text: string) => void
  addEvent: (event: SSEChunk) => void
  reset: () => void
}

export const useDemoStore = create<DemoState>((set) => ({
  isStreaming: false,
  output: '',
  events: [],
  setIsStreaming: (v) => set({ isStreaming: v }),
  appendOutput: (text) => set((s) => ({ output: s.output + text })),
  addEvent: (event) => set((s) => ({ events: [...s.events, event] })),
  reset: () => set({ isStreaming: false, output: '', events: [] }),
}))
