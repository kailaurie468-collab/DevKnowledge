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
