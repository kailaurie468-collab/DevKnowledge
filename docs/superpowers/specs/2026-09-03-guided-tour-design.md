# 首次使用引导（Guided Tour）设计文档

- 日期：2026-09-03
- 状态：已与用户确认
- 范围：仅前端（`frontend/**`），不涉及后端与数据库

## 1. 背景与目标

网站功能页较多（知识搜索 / Demo 生成 / Skills / 知识库 / Wiki / 设置），新用户首次进入后不知道从哪里开始。目标：首次进入功能页时，用引导小窗（聚光灯式）带领用户跨页面走一遍主流程，可随时跳过。

主流程：**配置 AI 服务 → Demo 生成 → 建知识库**。

## 2. 触发与持久化

- **触发条件**（同时满足）：
  1. 用户已登录；
  2. 当前路由属于功能页：`/knowledge`、`/demos`、`/skills`、`/kb`、`/wiki`、`/settings/*`（即非 `/`、`/login`、`/admin`）；
  3. `localStorage` 中无引导完成记录。
- **"首次"判定**：`localStorage` key `devknowledge.tour.done.v1`。值为 `done`（完整看完）或 `skipped`（跳过），两者都视为"看过"，不再自动触发。按浏览器存储，不进后端。
- **重看入口**：Header 右上角新增 `?` 图标按钮，点击随时重看引导（不检查 localStorage，看完/跳过同样只更新记录）。
- **Wiki / Skills 不进主流程步骤**（模块待完善），仅在欢迎步骤文案中提及。

## 3. 引导步骤（5 步）

| # | 页面 | 聚光灯目标（data-tour 属性） | 内容要点 |
|---|------|------------------------------|---------|
| 1 | 触发时所在页 | 侧边栏容器 `[data-tour="sidebar"]` | 欢迎语 + 30 秒过一遍核心玩法；介绍左侧全局导航；提示「跳过」随时可用 |
| 2 | `/settings/ai` | 「+ 添加新配置」按钮 `[data-tour="ai-add-btn"]` | 前置关键：先添加 OpenAI 兼容的 AI 服务，保存后用「测试连接」验证，才能生成 Demo |
| 3 | `/demos` | 需求输入框 `[data-tour="demo-prompt"]` | 核心玩法：一句话描述想要的 Demo，ReAct Agent 自动检索知识、多轮推理、流式生成可运行代码 |
| 4 | `/kb` | 「新建知识库」按钮 `[data-tour="kb-create-btn"]` | 进阶：上传 md/pdf/docx 文档建知识库（需先配 Embedding），Demo 生成时可挂 RAG 混合检索 |
| 5 | 就地结束（当前页） | 无目标，居中小结卡片 | 完成！从侧边栏开始探索，遇到问题用右上角反馈 |

- 每步气泡常驻：步骤进度（第 N 步 / 共 5 步）、「跳过」按钮、「上一步」（第 1 步隐藏）、「下一步」/「完成」。
- 跳过 = 立即结束引导。

## 4. 技术方案

### 4.1 选型

- 引擎：**driver.js 1.8.0**（纯 DOM 库，无 React 版本耦合，聚光灯/气泡定位/自动滚动/键盘导航内置，gzip 约 5KB）。peerDependencies 无 React 限制。
- 跨页编排（步骤状态机 + 路由同步 + 等元素挂载）**自研**——这是各引导库都不覆盖的部分。
- 样式：driver.js popover 通过 `popoverClass` 接 Tailwind 原子类（Tailwind v4 扫描 `.ts` 文件中的类名字符串），配 `dark:` 前缀适配暗色主题；遮罩用项目半透明黑。

### 4.2 文件结构

新增（3 个）：

```
frontend/src/
├── stores/tourStore.ts            # Zustand：状态 + 动作
├── components/tour/tourSteps.ts   # 步骤定义（纯数据）
└── components/tour/GuidedTour.tsx # 编排器组件
```

修改（6 个，均为小改）：

- `components/layout/Sidebar.tsx`：侧边栏容器加 `data-tour="sidebar"`
- `pages/settings/AiSettings.tsx`：「+ 添加新配置」按钮加 `data-tour="ai-add-btn"`
- `pages/DemoPage.tsx`：需求输入框加 `data-tour="demo-prompt"`
- `pages/KbPage.tsx`：「新建知识库」按钮加 `data-tour="kb-create-btn"`
- `components/layout/Header.tsx`：右上角加 `?` 重看按钮（Header 只在功能页渲染，首页是 CardNav 不动——重看入口覆盖功能页即可，首页用户点进任一功能页可见）
- `components/layout/Layout.tsx`：`npm install driver.js` 后，在 Layout 组件内挂载 `<GuidedTour />`（Layout 是路由元素、位于 BrowserRouter 内，`useNavigate` / `useLocation` 可直接使用；/login 不在 Layout 内，天然不触发）

### 4.3 编排器核心逻辑（GuidedTour.tsx）

状态机（Zustand `tourStore`）：

- `active: boolean`、`stepIndex: number`、`startPath: string`
- `start()` / `next()` / `prev()` / `skip()` / `finish()`

流程：

1. 监听 `location` + 登录态（`useEffect`）：满足触发条件且 localStorage 无记录 → `start()`，记录起始路由。
2. `stepIndex` 变化时：若当前步骤的 `page` ≠ 当前路由 → `navigate(page)`。
   - **区分自跳转与用户跳转**：编排器用 `expectedPathRef` 记录"预期路由"——`navigate()` 前先写入目标路径；路由变化的 effect 中若 `pathname !== expectedPathRef.current` 则视为用户手动导航 → 自动结束。否则会把编排器自己的跳转误判为用户操作，造成"点下一步即结束"的 bug。
3. **等元素挂载**：`waitForElement(selector)`（MutationObserver 轮询 DOM + 3s 超时）。元素就绪后创建 driver 实例指向它，解决跨页后 React 未渲染完的时序问题。
4. **每步重建 driver 实例**（单页内切步同样重建，简单可靠）。
5. 结束时（跳过/完成）销毁实例并写 localStorage。

### 4.4 细节约定

- 引导中的路由跳转（第 2/3/4 步）由编排器通过 `navigate()` 完成，用户在气泡点「下一步」才走。
- 步骤定义中的目标选择器统一用 `[data-tour="xxx"]`，语义清晰且不依赖样式类变动。
- popover 上的按钮通过 driver.js 的 `onPopoverRender` 注入自定义 DOM（React 按钮或原生 button + Tailwind 类）。

## 5. 边界情况

| 情况 | 处理 |
|------|------|
| 引导中用户手动改路由 / 浏览器后退 | 编排器发现当前路由与步骤预期不符 → 自动结束并写 localStorage（视为看过） |
| 引导中刷新页面 | 引导状态仅在内存，不恢复；localStorage 未写 → 下次进功能页从第 1 步重新触发 |
| 引导中退出登录 | 强制结束，不写 localStorage 记录 |
| 目标元素 3s 内未出现 | 该步降级为居中卡片显示文案（无聚光灯目标），不卡死流程 |
| 未登录进功能页 | 不触发（主流程需要登录） |

## 6. 测试与验收

前端无单测基建（遵循 frontend/CLAUDE.md，不编造测试脚本），验收方式：

1. `cd frontend && npm run build`（含 `tsc -b`）通过。
2. 手动走查清单：
   - 清 localStorage → 登录 → 进功能页：引导自动触发；
   - 五步跨页顺序走通（含聚光灯定位、气泡样式）；
   - 「跳过」后不再自动触发；走完「完成」后同样不再触发；
   - Header `?` 按钮可重看；
   - 引导中手动点侧边栏改路由 → 引导自动结束；
   - 暗色模式样式正常；
   - 未登录不触发。
3. 更新 `进度.md` 记录本功能。
