import { create } from 'zustand'
import { TOUR_STEPS } from '@/components/tour/tourSteps'

/** localStorage key：值 'done'（完整看完）/ 'skipped'（跳过），两者都视为「看过」，不再自动触发 */
const TOUR_KEY = 'devknowledge.tour.done.v1'

/** 引导是否已看过（自动触发判定依据） */
export function isTourDone(): boolean {
  try {
    return localStorage.getItem(TOUR_KEY) !== null
  } catch {
    // 隐私模式等场景下静默失败，当作未看过
    return false
  }
}

/** 写入引导完成记录 */
function markTourDone(result: 'done' | 'skipped'): void {
  try {
    localStorage.setItem(TOUR_KEY, result)
  } catch {
    // 写入失败时忽略，最多下次再触发一次
  }
}

interface TourState {
  /** 引导是否进行中 */
  active: boolean
  /** 当前步骤下标 */
  stepIndex: number
  /** 触发引导时所在路由 */
  startPath: string
  /** 开始引导（从第 1 步）；已在进行中则忽略 */
  start: (startPath: string) => void
  next: () => void
  prev: () => void
  /** 跳过：结束并写 'skipped' 记录（视为看过，不再自动触发） */
  skip: () => void
  /** 完成：结束并写 'done' 记录 */
  finish: () => void
  /** 强制结束（退出登录 / 组件卸载），不写任何记录 */
  reset: () => void
}

export const useTourStore = create<TourState>((set, get) => ({
  active: false,
  stepIndex: 0,
  startPath: '/',

  start: (startPath) => {
    if (get().active) return
    set({ active: true, stepIndex: 0, startPath })
  },

  next: () => set(s => ({ stepIndex: Math.min(s.stepIndex + 1, TOUR_STEPS.length - 1) })),

  prev: () => set(s => ({ stepIndex: Math.max(s.stepIndex - 1, 0) })),

  skip: () => {
    markTourDone('skipped')
    set({ active: false, stepIndex: 0 })
  },

  finish: () => {
    markTourDone('done')
    set({ active: false, stepIndex: 0 })
  },

  reset: () => set({ active: false, stepIndex: 0 }),
}))
