import { useRef, useState, useEffect } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { LiquidGlass } from '@/components/effects/LiquidGlass'

const links = [
  { to: '/', label: '首页', icon: ' ' },
  { to: '/knowledge', label: '知识搜索', icon: ' ' },
  { to: '/demos', label: 'Demo 生成', icon: ' ' },
  { to: '/skills', label: 'Skills 构建', icon: ' ' },
  { to: '/kb', label: '知识库', icon: ' ' },
  { to: '/wiki', label: 'Wiki 知识图谱', icon: ' ' },
]

const settingsChildren = [
  { to: '/settings/ai', label: 'AI 服务配置' },
  { to: '/settings/embedding', label: 'Embedding AI' },
  { to: '/settings/storage', label: '数据存储' },
  { to: '/settings/rag-metrics', label: 'RAG 指标' },
]

export function Sidebar() {
  const navRef = useRef<HTMLElement>(null)
  const location = useLocation()
  const isSettingsActive = location.pathname.startsWith('/settings')

  // 路径在 settings 下时默认展开
  const [expanded, setExpanded] = useState(isSettingsActive)

  // 路由变化时自动展开
  useEffect(() => {
    if (isSettingsActive) setExpanded(true)
  }, [isSettingsActive])

  return (
    <aside className="w-56 border-r border-gray-200 bg-gray-50 dark:bg-gray-900 dark:border-gray-700 flex flex-col py-4 transition-colors">
      <nav ref={navRef} className="relative flex flex-col gap-1 px-2">
        {/* 液态玻璃覆盖层 */}
        <LiquidGlass
          containerRef={navRef}
          activeSelector="a.active"
          blur={20}
          duration={400}
        />

        {/* 普通链接 */}
        {links.map(({ to, label, icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `relative z-10 flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors duration-200 ${
                isActive
                  ? 'active text-primary-700 dark:text-primary-400 font-medium'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
              }`
            }
          >
            <span>{icon}</span>
            <span>{label}</span>
          </NavLink>
        ))}

        {/* 设置 — 可展开 */}
        <button
          onClick={() => setExpanded(!expanded)}
          className={`relative z-10 flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors duration-200 w-full text-left ${
            isSettingsActive
              ? 'text-primary-700 dark:text-primary-400 font-medium'
              : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
          }`}
        >
          <span> </span>
          <span className="flex-1">设置</span>
          <svg
            className={`w-3.5 h-3.5 transition-transform duration-200 ${expanded ? 'rotate-0' : '-rotate-90'}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
          </svg>
        </button>

        {/* 子菜单 */}
        <div
          className={`overflow-hidden transition-all duration-200 ${expanded ? 'max-h-48 opacity-100' : 'max-h-0 opacity-0'}`}
        >
          <div className="flex flex-col gap-0.5 pl-4">
            {settingsChildren.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `px-3 py-1.5 rounded-md text-xs transition-colors duration-200 ${
                    isActive
                      ? 'text-primary-700 dark:text-primary-400 font-medium bg-primary-50 dark:bg-primary-900/20'
                      : 'text-gray-500 hover:text-gray-800 dark:text-gray-500 dark:hover:text-gray-300'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
          </div>
        </div>
      </nav>
    </aside>
  )
}
