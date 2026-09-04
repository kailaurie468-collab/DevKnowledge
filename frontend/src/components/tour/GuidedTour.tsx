import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { driver } from 'driver.js'
import type { Driver } from 'driver.js'
import 'driver.js/dist/driver.css'
import { useAuthStore } from '@/stores/authStore'
import { isTourDone, useTourStore } from '@/stores/tourStore'
import { TOUR_STEPS, type TourStep } from './tourSteps'

/** 触发引导的功能页前缀（首页 /、/login、/admin 不触发） */
const FUNCTIONAL_PREFIXES = ['/knowledge', '/demos', '/skills', '/kb', '/wiki', '/settings']

function isFunctionalPage(pathname: string): boolean {
  return FUNCTIONAL_PREFIXES.some(p => pathname === p || pathname.startsWith(p + '/'))
}

/** 等待目标元素挂载（跨页导航后 React 可能尚未渲染完）；超时返回 false */
function waitForElement(selector: string, timeout = 3000): Promise<boolean> {
  return new Promise(resolve => {
    if (document.querySelector(selector)) {
      resolve(true)
      return
    }
    const timer = window.setTimeout(() => {
      observer.disconnect()
      resolve(false)
    }, timeout)
    const observer = new MutationObserver(() => {
      if (document.querySelector(selector)) {
        window.clearTimeout(timer)
        observer.disconnect()
        resolve(true)
      }
    })
    observer.observe(document.body, { childList: true, subtree: true })
  })
}

/** 居中卡片与 popover 共用的按钮样式 */
const BTN_SKIP =
  'px-3 py-1.5 rounded-md text-sm text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors'
const BTN_PREV =
  'px-3 py-1.5 rounded-md text-sm border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors'
const BTN_NEXT =
  'px-4 py-1.5 rounded-md text-sm font-medium bg-primary-600 text-white hover:bg-primary-700 transition-colors'

/** popover 容器样式：Tailwind v4 尾缀 ! 覆盖 driver.css 默认主题，适配暗色 */
const POPOVER_CLASS =
  'bg-white! dark:bg-gray-900! text-gray-900! dark:text-gray-100! ' +
  'border border-gray-200! dark:border-gray-700! rounded-xl! shadow-2xl! p-4! w-80!'

/** 往 driver popover 里注入进度与控制按钮（默认按钮已由 showButtons: [] 关闭） */
function renderPopoverControls(popoverWrapper: HTMLElement, index: number, total: number): void {
  const isLast = index === total - 1

  const progress = document.createElement('div')
  progress.className = 'text-xs text-gray-400 dark:text-gray-500 mb-3'
  progress.textContent = `第 ${index + 1} 步 / 共 ${total} 步`

  const controls = document.createElement('div')
  controls.className = 'flex items-center justify-between mt-4'

  const skipBtn = document.createElement('button')
  skipBtn.textContent = '跳过'
  skipBtn.className = BTN_SKIP
  skipBtn.onclick = () => useTourStore.getState().skip()

  const right = document.createElement('div')
  right.className = 'flex items-center gap-2'

  if (index > 0) {
    const prevBtn = document.createElement('button')
    prevBtn.textContent = '上一步'
    prevBtn.className = BTN_PREV
    prevBtn.onclick = () => useTourStore.getState().prev()
    right.appendChild(prevBtn)
  }

  const nextBtn = document.createElement('button')
  nextBtn.textContent = isLast ? '完成' : '下一步'
  nextBtn.className = BTN_NEXT
  nextBtn.onclick = () => {
    const s = useTourStore.getState()
    if (isLast) s.finish()
    else s.next()
  }
  right.appendChild(nextBtn)

  controls.append(skipBtn, right)
  popoverWrapper.append(progress, controls)
}

/** 无聚光灯目标的步骤（最后一步 / 目标超时降级）：居中小结卡片 */
function TourCenterCard({ step, index, total }: { step: TourStep; index: number; total: number }) {
  const { next, prev, skip, finish } = useTourStore()
  const isLast = index === total - 1

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 px-4">
      <div className="w-full max-w-sm bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-700 shadow-2xl p-6">
        <div className="text-xs text-gray-400 dark:text-gray-500 mb-2">{`第 ${index + 1} 步 / 共 ${total} 步`}</div>
        <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100 mb-2">{step.title}</h2>
        <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed">{step.content}</p>
        <div className="flex items-center justify-between mt-6">
          <button onClick={skip} className={BTN_SKIP}>
            跳过
          </button>
          <div className="flex items-center gap-2">
            {index > 0 && (
              <button onClick={prev} className={BTN_PREV}>
                上一步
              </button>
            )}
            <button onClick={isLast ? finish : next} className={BTN_NEXT}>
              {isLast ? '完成' : '下一步'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

/** 引导编排器：自动触发、跨页导航、聚光灯渲染、用户手动导航检测 */
export function GuidedTour() {
  const active = useTourStore(s => s.active)
  const stepIndex = useTourStore(s => s.stepIndex)
  const isAuthenticated = useAuthStore(s => s.isAuthenticated)
  const location = useLocation()
  const navigate = useNavigate()

  const driverRef = useRef<Driver | null>(null)
  /** 编排器预期当前应处的路由：用于区分自跳转与用户手动导航 */
  const expectedPathRef = useRef<string>('')
  /** 当前以居中卡片渲染的步骤（无目标，或目标超时降级） */
  const [centerStep, setCenterStep] = useState<TourStep | null>(null)
  /** 卸载时若引导仍在进行，需要重置 store */
  const activeRef = useRef(active)
  activeRef.current = active

  const destroyDriver = () => {
    driverRef.current?.destroy()
    driverRef.current = null
  }

  // 1) 自动触发：登录 + 功能页 + 无完成记录
  useEffect(() => {
    if (active) return
    if (!isAuthenticated) return
    if (!isFunctionalPage(location.pathname)) return
    if (isTourDone()) return
    useTourStore.getState().start(location.pathname)
  }, [active, isAuthenticated, location.pathname])

  // 2) active 翻转为 true 时以当前路由为基线（Header ? 重看入口也走这里）
  useEffect(() => {
    if (active) expectedPathRef.current = location.pathname
  }, [active])

  // 3) 步骤指定页面时导航过去。只监听 stepIndex：用户手动导航由 effect 4 结束引导，这里不能把用户「拉回来」
  useEffect(() => {
    if (!active) return
    const step = TOUR_STEPS[stepIndex]
    if (!step?.page) return
    if (location.pathname !== step.page) {
      expectedPathRef.current = step.page
      navigate(step.page)
    }
  }, [active, stepIndex, navigate])

  // 4) 用户手动导航到非预期路由 → 自动结束（视为看过，写 skipped）
  useEffect(() => {
    if (!active) return
    if (expectedPathRef.current === location.pathname) return
    useTourStore.getState().skip()
  }, [active, location.pathname])

  // 5) 引导中退出登录 → 强制结束，不写记录
  useEffect(() => {
    if (active && !isAuthenticated) useTourStore.getState().reset()
  }, [active, isAuthenticated])

  // 6) 渲染当前步骤。跨页步骤先等路由到位（本 effect 会因 pathname 变化重跑），再等元素挂载
  useEffect(() => {
    if (!active) return
    const step = TOUR_STEPS[stepIndex]
    if (!step) return
    let cancelled = false

    setCenterStep(null)

    // 跨页步骤且路由未到位：交给 effect 3 导航，pathname 变化后本 effect 重跑
    if (step.page && location.pathname !== step.page) return

    if (!step.target) {
      setCenterStep(step)
      return
    }
    const target = step.target

    waitForElement(target).then(found => {
      if (cancelled) return
      if (!found) {
        // 目标 3s 未挂载（模块异常/状态未渲染）→ 降级为居中卡片，不卡死流程
        setCenterStep(step)
        return
      }
      const d = driver({ allowClose: false })
      d.highlight({
        element: target,
        popover: {
          title: step.title,
          description: step.content,
          showButtons: [],
          popoverClass: POPOVER_CLASS,
          onPopoverRender: popover =>
            renderPopoverControls(popover.wrapper, stepIndex, TOUR_STEPS.length),
        },
      })
      driverRef.current = d
    })

    return () => {
      cancelled = true
      destroyDriver()
    }
  }, [active, stepIndex, location.pathname])

  // 7) 卸载清理：销毁 driver；引导仍在进行则重置 store（不写记录）
  useEffect(() => {
    return () => {
      driverRef.current?.destroy()
      driverRef.current = null
      if (activeRef.current) useTourStore.getState().reset()
    }
  }, [])

  if (!active) return null
  return centerStep ? (
    <TourCenterCard step={centerStep} index={stepIndex} total={TOUR_STEPS.length} />
  ) : null
}
