import { useEffect, useState } from 'react'
import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { AdminFeedbackSection } from '@/components/admin/AdminFeedbackSection'
import { AdminOverviewSection } from '@/components/admin/AdminOverviewSection'
import { AdminTracesSection } from '@/components/admin/AdminTracesSection'
import { AdminUsersSection } from '@/components/admin/AdminUsersSection'

const adminTabs = [
  { to: '/admin', label: '概览', end: true },
  { to: '/admin/users', label: '用户', end: false },
  { to: '/admin/traces', label: '请求与错误', end: false },
  { to: '/admin/feedback', label: '反馈', end: false },
]

/**
 * 开发者后台壳：tab 导航 + 子路由。
 * 接口由后端邮箱白名单做最终鉴权，非管理员访问会被 403 拦截弹回首页。
 */
export function AdminPage() {
  const [accessDenied, setAccessDenied] = useState(false)

  // 子区块收到 403 时通知到壳统一处理（避免每个子组件重复写跳转）
  useEffect(() => {
    const handler = () => setAccessDenied(true)
    window.addEventListener('admin-access-denied', handler)
    return () => window.removeEventListener('admin-access-denied', handler)
  }, [])

  if (accessDenied) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">开发者后台</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          请求、错误、用户和反馈概览
        </p>
      </div>

      {/* 后台内导航 */}
      <nav className="flex gap-1 border-b border-gray-200 dark:border-gray-700">
        {adminTabs.map(tab => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              `px-4 py-2 text-sm transition-colors ${
                isActive
                  ? 'border-b-2 border-primary-600 font-medium text-primary-600 dark:text-primary-400'
                  : 'text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
              }`
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <Routes>
        <Route index element={<AdminOverviewSection />} />
        <Route path="users" element={<AdminUsersSection />} />
        <Route path="traces" element={<AdminTracesSection />} />
        <Route path="feedback" element={<AdminFeedbackSection />} />
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Routes>
    </div>
  )
}
