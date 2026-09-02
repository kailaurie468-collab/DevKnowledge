import { Link } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { ThemeToggle } from '@/components/effects/ThemeToggle'
import { FeedbackDialog } from '@/components/FeedbackDialog'

export function Header() {
  const { user, isAuthenticated, logout } = useAuthStore()

  return (
    <header className="h-14 border-b border-gray-200 bg-white/80 dark:bg-gray-900/60 backdrop-blur-md dark:border-gray-700 flex items-center justify-between px-4 transition-colors relative z-20">
      <Link to="/" className="text-lg font-bold text-primary-600 dark:text-primary-400">
        DevKnowledge
      </Link>

      <nav className="flex items-center gap-4">
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
