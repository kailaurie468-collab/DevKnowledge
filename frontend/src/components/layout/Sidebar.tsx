import { useRef } from 'react'
import { NavLink } from 'react-router-dom'
import { LiquidGlass } from '@/components/effects/LiquidGlass'

const links = [
  { to: '/', label: '首页', icon: ' ' },
  { to: '/knowledge', label: '知识搜索', icon: ' ' },
  { to: '/demos', label: 'Demo 生成', icon: ' ' },
  { to: '/skills', label: 'Skills 构建', icon: ' ' },
  { to: '/kb', label: '知识库', icon: ' ' },
  { to: '/wiki', label: 'Wiki 知识图谱', icon: ' ' },
  { to: '/rag-metrics', label: 'RAG 指标', icon: ' ' },
  { to: '/settings', label: '设置', icon: ' ' },
]

export function Sidebar() {
  const navRef = useRef<HTMLElement>(null)

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
      </nav>
    </aside>
  )
}
