import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ShapeBlur from '@/components/ShapeBlur'

const modules = [
  {
    path: '/knowledge',
    title: '知识搜索',
    desc: '搜索框架文档，跳转到具体知识点锚点。',
    variation: 0,
  },
  {
    path: '/demos',
    title: 'Demo 生成',
    desc: '通过 AI 生成代码示例及解释。',
    variation: 1,
  },
  {
    path: '/skills',
    title: 'Skills 构建',
    desc: '从描述中提取工作流，导出为 Claude Code Skills。',
    variation: 2,
  },
  {
    path: '/kb',
    title: '知识库',
    desc: '上传文档，构建 RAG 驱动的知识库。',
    variation: 3,
  },
]

export function HomePage() {
  const navigate = useNavigate()
  const [hovered, setHovered] = useState<number | null>(null)

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-4">DevKnowledge</h1>
      <p className="text-gray-600 mb-8">
        开发者知识平台 — 搜索文档、生成 Demo、构建可复用 Skills. 致敬每一位坚持手搓的coder
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {modules.map((mod) => (
          <button
            key={mod.path}
            onClick={() => navigate(mod.path)}
            onMouseEnter={() => setHovered(mod.variation)}
            onMouseLeave={() => setHovered(null)}
            className="relative text-left p-5 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all overflow-hidden"
          >
            <div
              className="absolute inset-0 pointer-events-none transition-opacity duration-500"
              style={{ opacity: hovered === mod.variation ? 0.7 : 0 }}
            >
              <ShapeBlur
                variation={0}
                pixelRatioProp={window.devicePixelRatio || 1}
                shapeSize={2.1}
                roundness={0.5}
                borderSize={0.12}
                circleSize={0.5}
                circleEdge={0.8}
                color="#2563eb"
              />
            </div>
            <div className="relative z-10">
              <h2 className="font-semibold text-gray-900 mb-1">{mod.title}</h2>
              <p className="text-sm text-gray-500">{mod.desc}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}
