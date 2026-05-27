import { NavLink } from 'react-router-dom'

const links = [
  { to: '/', label: '首页', icon: ' ' },
  { to: '/knowledge', label: '知识搜索', icon: ' ' },
  { to: '/demos', label: 'Demo 生成', icon: ' ' },
  { to: '/skills', label: 'Skills 构建', icon: ' ' },
  { to: '/kb', label: '知识库', icon: ' ' },
  { to: '/rag-metrics', label: 'RAG 指标', icon: ' ' },
  { to: '/settings', label: '设置', icon: ' ' },
]

export function Sidebar() {
  return (
    <aside className="w-56 border-r border-gray-200 bg-gray-50 flex flex-col py-4">
      <nav className="flex flex-col gap-1 px-2">
        {links.map(({ to, label, icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors ${
                isActive
                  ? 'bg-primary-100 text-primary-700 font-medium'
                  : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
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
