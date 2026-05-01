import { Link } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

export function Header() {
  const { user, isAuthenticated, logout } = useAuthStore()

  return (
    <header className="h-14 border-b border-gray-200 bg-white flex items-center justify-between px-4">
      <Link to="/" className="text-lg font-bold text-primary-600">
        DevKnowledge
      </Link>

      <nav className="flex items-center gap-4">
        {isAuthenticated ? (
          <>
            <span className="text-sm text-gray-600">{user?.displayName || user?.email}</span>
            <button
              onClick={logout}
              className="text-sm text-gray-500 hover:text-gray-700"
            >
              退出
            </button>
          </>
        ) : (
          <Link
            to="/login"
            className="text-sm text-primary-600 hover:text-primary-700 font-medium"
          >
            登录
          </Link>
        )}
      </nav>
    </header>
  )
}
