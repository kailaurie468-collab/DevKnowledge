import { motion } from 'motion/react'
import { useThemeStore } from '@/stores/themeStore'

export function ThemeToggle() {
  const { theme, toggleTheme } = useThemeStore()
  const isDark = theme === 'dark'

  return (
    <button
      onClick={toggleTheme}
      className="relative w-14 h-7 rounded-full transition-colors duration-300 cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary-400 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
      style={{
        background: isDark
          ? 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)'
          : 'linear-gradient(135deg, #bae6fd 0%, #38bdf8 100%)',
        boxShadow: isDark
          ? 'inset 0 1px 3px rgba(0,0,0,0.4), 0 1px 2px rgba(0,0,0,0.2)'
          : 'inset 0 1px 3px rgba(0,0,0,0.1), 0 1px 2px rgba(0,0,0,0.05)',
      }}
      title={isDark ? '切换到白天模式' : '切换到黑夜模式'}
    >
      {/* 轨道装饰 */}
      <span className="absolute inset-0 overflow-hidden rounded-full pointer-events-none">
        {isDark && (
          <>
            <span className="absolute top-1.5 left-2 w-0.5 h-0.5 bg-white rounded-full opacity-80" />
            <span className="absolute top-3 left-4 w-0.5 h-0.5 bg-white rounded-full opacity-60" />
            <span className="absolute top-1 left-6 w-0.5 h-0.5 bg-white rounded-full opacity-70" />
          </>
        )}
        {!isDark && (
          <>
            <span className="absolute top-2 right-3 w-2.5 h-1 bg-white/50 rounded-full" />
            <span className="absolute top-3.5 right-5 w-1.5 h-0.5 bg-white/40 rounded-full" />
          </>
        )}
      </span>

      {/* 滑块 - pointer-events-none 让点击穿透到 button */}
      <motion.div
        animate={{
          x: isDark ? 28 : 2,
          rotate: isDark ? 180 : 0,
        }}
        transition={{
          type: 'spring',
          stiffness: 500,
          damping: 30,
        }}
        className="absolute top-0.5 w-6 h-6 rounded-full flex items-center justify-center pointer-events-none"
        style={{
          background: isDark
            ? 'linear-gradient(135deg, #e2e8f0, #cbd5e1)'
            : 'linear-gradient(135deg, #fef9c3, #fde047)',
          boxShadow: isDark
            ? '0 2px 6px rgba(0,0,0,0.3)'
            : '0 2px 6px rgba(0,0,0,0.15)',
        }}
      >
        {isDark ? (
          <svg className="w-3.5 h-3.5 text-slate-700" fill="currentColor" viewBox="0 0 24 24">
            <path d="M21.752 15.002A9.718 9.718 0 0118 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 003 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 009.002-5.998z" />
          </svg>
        ) : (
          <svg className="w-3.5 h-3.5 text-amber-600" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2.25a.75.75 0 01.75.75v2.25a.75.75 0 01-1.5 0V3a.75.75 0 01.75-.75zM7.5 12a4.5 4.5 0 119 0 4.5 4.5 0 01-9 0zM18.894 6.166a.75.75 0 00-1.06-1.06l-1.591 1.59a.75.75 0 101.06 1.061l1.591-1.59zM21.75 12a.75.75 0 01-.75.75h-2.25a.75.75 0 010-1.5H21a.75.75 0 01.75.75zM17.834 18.894a.75.75 0 001.06-1.06l-1.59-1.591a.75.75 0 10-1.061 1.06l1.59 1.591zM12 18a.75.75 0 01.75.75V21a.75.75 0 01-1.5 0v-2.25A.75.75 0 0112 18zM7.758 17.303a.75.75 0 00-1.061-1.06l-1.591 1.59a.75.75 0 001.06 1.061l1.591-1.59zM6 12a.75.75 0 01-.75.75H3a.75.75 0 010-1.5h2.25A.75.75 0 016 12zM6.697 7.757a.75.75 0 001.06-1.06l-1.59-1.591a.75.75 0 00-1.061 1.06l1.59 1.591z" />
          </svg>
        )}
      </motion.div>
    </button>
  )
}
