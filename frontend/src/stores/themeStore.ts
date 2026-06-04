import { create } from 'zustand'

type Theme = 'light' | 'dark'

interface ThemeState {
  theme: Theme
  toggleTheme: () => void
  setTheme: (theme: Theme) => void
}

// 默认白天模式，只在用户主动切换后才保存
function getInitialTheme(): Theme {
  const saved = localStorage.getItem('theme')
  if (saved === 'dark') return 'dark'
  return 'light'
}

// 应用主题到 <html> 元素
function applyTheme(theme: Theme) {
  const root = document.documentElement
  if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
  localStorage.setItem('theme', theme)
}

export const useThemeStore = create<ThemeState>((set) => {
  const initial = getInitialTheme()
  // 初始化时立即应用
  applyTheme(initial)

  return {
    theme: initial,
    toggleTheme: () => {
      set((state) => {
        const next = state.theme === 'light' ? 'dark' : 'light'
        applyTheme(next)
        return { theme: next }
      })
    },
    setTheme: (theme) => {
      applyTheme(theme)
      set({ theme })
    },
  }
})
