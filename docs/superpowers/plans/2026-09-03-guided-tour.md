# 首次使用引导（Guided Tour）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录用户首次进入功能页时，自动弹出跨页面的聚光灯式引导（5 步走完「配 AI → 生成 Demo → 建知识库」主流程），可跳过、可从 Header `?` 重看。

**Architecture:** Zustand 状态机（tourStore）+ 纯数据步骤定义（tourSteps）+ 挂在 Layout 的编排器组件（GuidedTour）。编排器负责自动触发、跨页 navigate、等目标元素挂载（MutationObserver + 3s 超时降级居中卡片）、区分自跳转与用户手动导航。单步聚光灯用 driver.js 1.8.0 的 `highlight()` API，每步重建实例。

**Tech Stack:** React 19 + TypeScript 5.8 strict、react-router-dom 7、Zustand 5、Tailwind CSS v4（class 模式暗色）、driver.js 1.8.0（新依赖）。

**Spec:** `docs/superpowers/specs/2026-09-03-guided-tour-design.md`（本计划实现该 spec，执行者应同时读它）

## Global Constraints

- 响应与注释语言：简体中文（仓库规则）。
- 前端样式只用 Tailwind 原子类，函数式组件 + Hooks（frontend/CLAUDE.md）。
- **验证方式**：前端无单测/lint 基建，每个任务的验证 = `cd frontend && npm run build`（含 `tsc -b`）必须通过；手动走查集中在 Task 5。
- **driver.js 1.8.0 API 事实**（已核对官方 d.ts，勿凭记忆改写）：
  - 单元素高亮：`const d = driver({ allowClose: false }); d.highlight({ element: '<选择器>', popover: {...} })`。顶层 `Config` **没有** `element` 选项。
  - `onPopoverRender?: (popover: PopoverDOM, opts) => void`——`popover` 是 `PopoverDOM` 对象，自定义控件挂到 `popover.wrapper`（HTMLElement）上。
  - 隐藏默认按钮：`popover.showButtons: []`（类型是数组，不是 boolean）。
  - CSS 引入：`import 'driver.js/dist/driver.css'`（在包 exports 白名单内）。
  - 类型导出：`import type { Driver } from 'driver.js'`。
- Tailwind v4 important 修饰符是**尾缀** `!`（如 `bg-white!`），用于 popoverClass 覆盖 driver.css 默认样式。
- `package-lock.json` 由 `npm install` 生成/更新，勿手改；`frontend/dist/`、`backend/target/` 等生成物勿动。
- **git 提交**：仓库硬性规则「未经用户确认不得 git commit」。计划含分任务提交步骤；执行开始前必须先向用户取得一次性授权，未取得则跳过所有提交步骤。

---

### Task 1: 安装 driver.js + 引导状态机 tourStore

**Files:**
- Modify: `frontend/package.json` / `frontend/package-lock.json`（npm 自动改）
- Create: `frontend/src/stores/tourStore.ts`

**Interfaces:**
- Consumes: `TOUR_STEPS`（Task 2 创建，本任务先写好 import——**注意：本任务单独 build 会因 tourSteps 不存在而失败，故 Task 1 与 Task 2 在同一提交内完成，或先执行 Task 2 再回来 build**。执行顺序建议：先跑 Step 1 安装，写 tourStore.ts，然后立即做 Task 2，两个任务一起 build、一起提交）。
- Produces: `useTourStore: { active: boolean; stepIndex: number; startPath: string; start(startPath: string): void; next(): void; prev(): void; skip(): void; finish(): void; reset(): void }`；`isTourDone(): boolean`。

- [ ] **Step 1: 安装 driver.js**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm install driver.js
```

Expected: `package.json` dependencies 出现 `"driver.js": "^1.8.0"`，无 peer 依赖警告。

- [ ] **Step 2: 写 `frontend/src/stores/tourStore.ts`**

```typescript
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
```

- [ ] **Step 3: 暂不单独 build**

tourStore.ts 依赖 Task 2 的 `tourSteps.ts`，先继续 Task 2，build 验证放在 Task 2 末尾。

---

### Task 2: 引导步骤定义 tourSteps

**Files:**
- Create: `frontend/src/components/tour/tourSteps.ts`

**Interfaces:**
- Consumes: 无（纯数据）。
- Produces: `interface TourStep { page: string | null; target: string | null; title: string; content: string }`；`const TOUR_STEPS: TourStep[]`（5 个元素，顺序即引导顺序）。目标选择器常量供 Task 3 的 `data-tour` 标记使用：`sidebar`、`ai-add-btn`、`demo-prompt`、`kb-create-btn`。

- [ ] **Step 1: 写 `frontend/src/components/tour/tourSteps.ts`**

```typescript
/** 引导单步定义（纯数据模块，无 React 依赖，tourStore 与 GuidedTour 共用） */
export interface TourStep {
  /** 需要导航到的路由；null 表示留在当前页 */
  page: string | null
  /** 聚光灯目标选择器（[data-tour=...]）；null 表示居中卡片（无目标） */
  target: string | null
  title: string
  content: string
}

export const TOUR_STEPS: TourStep[] = [
  {
    page: null,
    target: '[data-tour="sidebar"]',
    title: '👋 欢迎使用 DevKnowledge',
    content:
      '用 30 秒带你过一遍核心玩法。左侧是全局导航——知识搜索、Demo 生成、Skills、知识库、Wiki 图谱，随时可以点「跳过」。',
  },
  {
    page: '/settings/ai',
    target: '[data-tour="ai-add-btn"]',
    title: '第 1 站：配置 AI 服务',
    content:
      '生成 Demo 前，先在这里添加一个 OpenAI 兼容的 AI 服务（API 地址 + Key）。保存后点「测试连接」验证通过，就可以开始使用了。',
  },
  {
    page: '/demos',
    target: '[data-tour="demo-prompt"]',
    title: '第 2 站：生成你的第一个 Demo',
    content:
      '在输入框里一句话描述想要的代码，ReAct Agent 会自动检索知识、多轮推理，流式生成可运行的 Demo。',
  },
  {
    page: '/kb',
    target: '[data-tour="kb-create-btn"]',
    title: '第 3 站：搭建你的知识库',
    content:
      '上传 md / pdf / docx 文档建知识库（需先在设置里配好 Embedding AI）。之后生成 Demo 时选择知识库，就能挂上 RAG 混合检索。',
  },
  {
    page: null,
    target: null,
    title: '🎉 一切就绪！',
    content:
      '主流程就是：配 AI → 写需求生成 Demo → 建知识库增强检索。Wiki 图谱和 Skills 还在打磨中，欢迎从左侧导航探索。遇到问题点右上角的反馈按钮。',
  },
]
```

- [ ] **Step 2: 构建验证（覆盖 Task 1 + Task 2）**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run build
```

Expected: `tsc -b` 无错误、vite build 成功。（此时 GuidedTour 尚未创建，tourSteps/tourStore 无消费者，但类型必须编译通过。）

- [ ] **Step 3: 提交（Task 1 + Task 2 一起）**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge
git add frontend/package.json frontend/package-lock.json frontend/src/stores/tourStore.ts frontend/src/components/tour/tourSteps.ts
git commit -m "feat(tour): 新增引导状态机与步骤定义

- tourStore：active/stepIndex 状态机 + localStorage 首次判定（done/skipped 均视为看过）
- tourSteps：5 步主流程定义（侧边栏欢迎 → AI 配置 → Demo 输入 → 知识库 → 结束卡）
- 接入 driver.js 1.8.0 依赖

Co-Authored-By: Claude <noreply@anthropic.com>"
```

（提交前确认已按 Global Constraints 取得用户授权）

---

### Task 3: 目标元素 data-tour 标记 + Header 重看入口

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx:36`（aside 标签）
- Modify: `frontend/src/pages/settings/AiSettings.tsx:203`（「+ 添加新配置」按钮）
- Modify: `frontend/src/pages/DemoPage.tsx:195`（需求输入 textarea）
- Modify: `frontend/src/pages/KbPage.tsx:457`（「新建知识库」按钮）
- Modify: `frontend/src/components/layout/Header.tsx`（新增 `?` 按钮）

**Interfaces:**
- Consumes: `useTourStore.getState().start(path)`（Task 1）。
- Produces: DOM 标记 `[data-tour="sidebar"]`、`[data-tour="ai-add-btn"]`、`[data-tour="demo-prompt"]`、`[data-tour="kb-create-btn"]`（Task 4 的步骤选择器依赖这些值，必须与 tourSteps.ts 中的完全一致）。

- [ ] **Step 1: Sidebar.tsx——aside 加标记**

`frontend/src/components/layout/Sidebar.tsx:36`，原代码：

```tsx
    <aside className="w-56 border-r border-gray-200 bg-gray-50 dark:bg-gray-900 dark:border-gray-700 flex flex-col py-4 transition-colors">
```

改为：

```tsx
    <aside data-tour="sidebar" className="w-56 border-r border-gray-200 bg-gray-50 dark:bg-gray-900 dark:border-gray-700 flex flex-col py-4 transition-colors">
```

- [ ] **Step 2: AiSettings.tsx——「+ 添加新配置」按钮加标记**

`frontend/src/pages/settings/AiSettings.tsx:203`，原代码：

```tsx
            <button
              onClick={handleNew}
              className="w-full p-3 border border-dashed border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-500 dark:text-gray-400 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
            >
              + 添加新配置
            </button>
```

改为（只在 button 上加一行属性）：

```tsx
            <button
              data-tour="ai-add-btn"
              onClick={handleNew}
              className="w-full p-3 border border-dashed border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-500 dark:text-gray-400 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
            >
              + 添加新配置
            </button>
```

- [ ] **Step 3: DemoPage.tsx——需求输入框加标记**

`frontend/src/pages/DemoPage.tsx:195`，原代码：

```tsx
        <textarea
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
```

改为：

```tsx
        <textarea
          data-tour="demo-prompt"
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
```

- [ ] **Step 4: KbPage.tsx——「新建知识库」按钮加标记**

`frontend/src/pages/KbPage.tsx:457`，原代码：

```tsx
        <button
          onClick={handleClickCreate}
          className="mb-6 px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700"
        >
          新建知识库
        </button>
```

改为：

```tsx
        <button
          data-tour="kb-create-btn"
          onClick={handleClickCreate}
          className="mb-6 px-4 py-2 bg-primary-600 text-white rounded-md text-sm font-medium hover:bg-primary-700"
        >
          新建知识库
        </button>
```

- [ ] **Step 5: Header.tsx——新增 `?` 重看按钮**

`frontend/src/components/layout/Header.tsx` 三处修改。

① 文件头部 import 区（第 1-6 行）改为：

```tsx
import { Link, useLocation } from 'react-router-dom'
import { FiHelpCircle } from 'react-icons/fi'
import { useAuthStore } from '@/stores/authStore'
import { useTourStore } from '@/stores/tourStore'
import { ThemeToggle } from '@/components/effects/ThemeToggle'
import { FeedbackDialog } from '@/components/FeedbackDialog'
```

② 组件内取当前路由（`export function Header()` 之后）：

```tsx
export function Header() {
  const { user, isAuthenticated, logout } = useAuthStore()
  const location = useLocation()
```

③ `<nav className="flex items-center gap-4">` 内、`<ThemeToggle />` 之前插入：

```tsx
      <nav className="flex items-center gap-4">
        {/* 引导重看入口：不检查 localStorage，随时从头看 */}
        <button
          onClick={() => useTourStore.getState().start(location.pathname)}
          title="重看使用引导"
          className="text-gray-400 hover:text-gray-600 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
        >
          <FiHelpCircle className="w-5 h-5" />
        </button>
        <ThemeToggle />
```

说明：`start()` 内部有 `if (get().active) return` 守卫，引导进行中重复点击无副作用，无需组件层判断。

- [ ] **Step 6: 构建验证**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run build
```

Expected: 通过。

- [ ] **Step 7: 提交**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/Header.tsx frontend/src/pages/settings/AiSettings.tsx frontend/src/pages/DemoPage.tsx frontend/src/pages/KbPage.tsx
git commit -m "feat(tour): 目标元素加 data-tour 标记，Header 新增引导重看入口

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: GuidedTour 编排器 + 挂载 Layout

**Files:**
- Create: `frontend/src/components/tour/GuidedTour.tsx`
- Modify: `frontend/src/components/layout/Layout.tsx`

**Interfaces:**
- Consumes: `useTourStore`、`isTourDone`（Task 1）；`TOUR_STEPS`、`TourStep`（Task 2）；`[data-tour=...]` DOM 标记（Task 3）；`useAuthStore(s => s.isAuthenticated)`。
- Produces: `export function GuidedTour(): JSX.Element | null`，挂载于 Layout，全站仅此一处实例。

- [ ] **Step 1: 写 `frontend/src/components/tour/GuidedTour.tsx`（完整文件）**

```tsx
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
```

- [ ] **Step 2: Layout.tsx 挂载**

`frontend/src/components/layout/Layout.tsx` 两处修改。

① import 区加一行（现有 import 之后）：

```tsx
import { GuidedTour } from '@/components/tour/GuidedTour'
```

② return 的 JSX 里，`</main>` 所在的 `<div className="flex flex-1 overflow-hidden">` 闭合之后、组件根 div 闭合之前插入（即 `</div>` 与 `)` 之间）：

```tsx
      </div>

      {/* 首次使用引导（挂在 Layout 内：功能页与首页都在，触发条件在组件内判断） */}
      <GuidedTour />
    </div>
  )
}
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run build
```

Expected: `tsc -b` 无错误、vite build 成功。常见报错自查：`popover.wrapper` 拼写（PopoverDOM 的字段名）、`showButtons: []`（数组字面量）、Tailwind 类尾缀 `!` 位置。

- [ ] **Step 4: 提交**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge
git add frontend/src/components/tour/GuidedTour.tsx frontend/src/components/layout/Layout.tsx
git commit -m "feat(tour): GuidedTour 编排器——跨页导航、聚光灯渲染与降级兜底

- 自动触发：登录 + 功能页 + localStorage 无记录，三条件同时满足
- expectedPathRef 区分自跳转与用户手动导航，手动导航即结束并记为看过
- waitForElement 等待跨页元素挂载，3s 超时降级居中卡片
- 退出登录 / Layout 卸载时强制结束且不写记录

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 手动走查验收 + 文档同步

**Files:**
- Modify: `进度.md`
- Modify: `CODE_MAP.md`

**Interfaces:**
- Consumes: Task 1-4 的全部产出。
- Produces: 无代码产出，验收与文档记录。

- [ ] **Step 1: 启动 dev 服务器**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run dev
```

打开 http://localhost:5173（后端未起也可走查引导 UI，登录态需要有本地 token；若无法登录，至少用 Header `?` 按钮走查第 2-6 项）。

- [ ] **Step 2: 手动走查清单（对照 spec 第 6 节，逐项勾选）**

1. 清 localStorage（DevTools → Application → Local Storage 删除 `devknowledge.tour.done.v1`）→ 登录 → 点进任一功能页（如 /demos）：引导自动触发，聚光灯照亮侧边栏，气泡显示「第 1 步 / 共 5 步」。
2. 依次点「下一步」：自动跳转 /settings/ai → /kb，每页聚光灯定位到对应元素（添加配置按钮 / 输入框 / 新建知识库按钮），最后一步为居中卡片；点「完成」后 localStorage 出现 `devknowledge.tour.done.v1 = done`。
3. 再刷新/切页：引导不再自动触发。
4. 重复走一次但在中途点「跳过」：引导立即结束，localStorage 值为 `skipped`，不再自动触发。
5. 引导进行中直接点侧边栏其他导航项：引导自动结束（不弹报错）。
6. Header 右上角 `?` 按钮：点击后引导从头开始，可完整重看。
7. 切换暗色模式（右上角主题开关）：popover 与居中卡片均为深色主题，文字可读。
8. 未登录状态（清 token）进入功能页：不触发引导。
9. 引导中点「上一步」：能回退，跨页步骤正确跳回上一页。

- [ ] **Step 3: 更新 `进度.md`**

在 `### Markdown 切分质量评估 🔲` 小节标题行之前插入：

```markdown
### 首次使用引导（Guided Tour）✅

- [X] **跨页面主流程引导** — driver.js 1.8.0 聚光灯式引导，5 步走完「侧边栏欢迎 → AI 服务配置 → Demo 输入 → 知识库创建 → 结束卡」；登录后首次进功能页自动触发（localStorage `devknowledge.tour.done.v1`，done/skipped 均视为看过）；引导中手动导航/退出登录自动结束；Header 右上角 `?` 可随时重看
- [X] **新增前端文件** — `stores/tourStore.ts`（状态机 + localStorage 首次判定）、`components/tour/tourSteps.ts`（步骤定义）、`components/tour/GuidedTour.tsx`（编排器：自动触发 / expectedPathRef 区分自跳转与手动导航 / waitForElement 等元素挂载，3s 超时降级居中卡片）

```

- [ ] **Step 4: 更新 `CODE_MAP.md`**

读 `CODE_MAP.md` 中「前端关键文件」表（含 `frontend/src/stores/demoStore.ts`、`frontend/src/stores/authStore.ts` 行），在 `authStore` 行后插入一行：

```markdown
| `frontend/src/components/tour/GuidedTour.tsx` | ★★☆ | 首次使用引导编排器（driver.js 聚光灯 + 跨页导航 + 降级兜底） |
```

- [ ] **Step 5: 提交**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge
git add 进度.md CODE_MAP.md
git commit -m "docs(tour): 引导功能走查验收，同步进度与代码地图

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 自审记录（写计划时已核对）

1. **Spec 覆盖**：触发条件（Task 4 effect 1）、5 步内容（Task 2）、重看入口（Task 3 Step 5）、手动导航结束（effect 4）、刷新不恢复（状态仅在内存，Task 1 store 无持久化）、退出登录不写记录（effect 5/7）、3s 超时降级（effect 6）、未登录不触发（effect 1）——spec 第 2/3/4/6 节全部有对应实现。
2. **API 事实**：driver.js 1.8.0 的 `highlight()` / `PopoverDOM.wrapper` / `showButtons: []` / `dist/driver.css` 均已对照官方 d.ts 与 package.json exports 核实，非记忆复述。
3. **类型一致性**：`data-tour` 值四处标记（Task 3）与 tourSteps.ts 选择器（Task 2）逐一对应：`sidebar` / `ai-add-btn` / `demo-prompt` / `kb-create-btn`；store 方法名 `start/next/prev/skip/finish/reset` 在 Task 1 定义、Task 4 消费处一致。
4. **已知取舍**：步骤切换时 driver 实例销毁重建，遮罩有瞬间闪动——spec 4.3 已明示「每步重建（简单可靠）」，属预期行为。
