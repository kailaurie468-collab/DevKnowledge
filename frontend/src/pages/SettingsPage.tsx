import { Outlet } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

export function SettingsPage() {
  const { isAuthenticated } = useAuthStore()

  if (!isAuthenticated) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">设置</h1>
        <p className="text-gray-500 dark:text-gray-400">请先登录以配置服务。</p>
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">设置</h1>
      <Outlet />
    </div>
  )
}
