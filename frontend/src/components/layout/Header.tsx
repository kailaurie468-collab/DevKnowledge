import { Link, useLocation } from 'react-router-dom'
import { FiHelpCircle } from 'react-icons/fi'
import { useAuthStore } from '@/stores/authStore'
import { useTourStore } from '@/stores/tourStore'
import { ThemeToggle } from '@/components/effects/ThemeToggle'
import { FeedbackDialog } from '@/components/FeedbackDialog'

export function Header() {
  const { user, isAuthenticated, logout } = useAuthStore()
  const location = useLocation()

  return (
    <header className="h-14 border-b border-gray-200 bg-white/80 dark:bg-gray-900/60 backdrop-blur-md dark:border-gray-700 flex items-center justify-between px-4 transition-colors relative z-20">
      <Link to="/" className="text-lg font-bold text-primary-600 dark:text-primary-400">
        DevKnowledge
      </Link>

      <nav className="flex items-center gap-4">
        {/* 引导重看入口：不检查 localStorage，随时从头看 */}
        <button
          onClick={() => useTourStore.getState().start(location.pathname)}
          title="重看使用引导"
          className="text-gray-400 hover:text-gray-600 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
        >
          <FiHelpCircle className="w-5 h-5" />
        </button>
        <ThemeToggle />
        <FeedbackDialog />
        {isAuthenticated ? (
          <>
            <span className="text-sm text-gray-600 dark:text-gray-300">{user?.displayName || user?.email}</span>
            <button
              onClick={logout}
              className="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
            >
              退出
            </button>
          </>
        ) : (
          <Link
            to="/login"
            className="text-sm text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300 font-medium"
          >
            登录
          </Link>
        )}
      </nav>
    </header>
  )
}
