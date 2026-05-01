import { useNavigate } from 'react-router-dom'

export function HomePage() {
  const navigate = useNavigate()

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-4">DevKnowledge</h1>
      <p className="text-gray-600 mb-8">
        开发者知识平台 — 搜索文档、生成 Demo、构建可复用 Skills
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <button
          onClick={() => navigate('/knowledge')}
          className="text-left p-5 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
        >
          <h2 className="font-semibold text-gray-900 mb-1">知识搜索</h2>
          <p className="text-sm text-gray-500">搜索框架文档，跳转到具体知识点锚点。</p>
        </button>
        <button
          onClick={() => navigate('/demos')}
          className="text-left p-5 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
        >
          <h2 className="font-semibold text-gray-900 mb-1">Demo 生成</h2>
          <p className="text-sm text-gray-500">通过 AI 生成代码示例及解释。</p>
        </button>
        <button
          onClick={() => navigate('/skills')}
          className="text-left p-5 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
        >
          <h2 className="font-semibold text-gray-900 mb-1">Skills 构建</h2>
          <p className="text-sm text-gray-500">从描述中提取工作流，导出为 Claude Code Skills。</p>
        </button>
        <button
          onClick={() => navigate('/kb')}
          className="text-left p-5 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
        >
          <h2 className="font-semibold text-gray-900 mb-1">知识库</h2>
          <p className="text-sm text-gray-500">上传文档，构建 RAG 驱动的知识库。</p>
        </button>
      </div>
    </div>
  )
}
