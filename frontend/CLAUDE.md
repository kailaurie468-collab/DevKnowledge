# frontend — AI 开发指南

先读根 `CLAUDE.md` 的硬性规则，再读本文件。模块落点以 `CODE_MAP.md` 为准。

## 适用场景

页面、组件、路由、SSE 流式、Zustand、前端 API 客户端。

## 约定

- 函数式组件 + Hooks；样式用 Tailwind 原子类，不新增 CSS 框架。
- 新接口客户端放 `src/api/`，类型放 `src/types/`，页面放 `src/pages/`，可复用 UI 放 `src/components/`。
- HTTP / SSE 基础能力只放在 `src/api/client.ts`，业务模块不要各自再写一份 fetch 封装。
- Demo 流式状态走 `src/hooks/useSSE.ts` + `src/stores/demoStore.ts`，切换页面时内容应保持，不要在页面组件里另起一份流式 store。
- 认证状态只走 `src/stores/authStore.ts`；请求带 `Authorization: Bearer <token>`。

## 自检

改动 `.ts` / `.tsx` 后必须在 `frontend/` 下运行 `npm run build`（含 `tsc -b`）。本目录没有 `lint` / 单测脚本，不要编造。
