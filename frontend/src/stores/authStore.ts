import { create } from 'zustand'
import type { User } from '@/types/api'
import { authApi } from '@/api/auth'

/** token 存储工具：根据「记住我」选择 localStorage 或 sessionStorage */
const tokenStore = {
  get(key: string): string | null {
    return localStorage.getItem(key) || sessionStorage.getItem(key)
  },
  set(key: string, value: string, remember: boolean) {
    if (remember) {
      localStorage.setItem(key, value)
      sessionStorage.removeItem(key)
    } else {
      sessionStorage.setItem(key, value)
      localStorage.removeItem(key)
    }
  },
  remove(key: string) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  },
}

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (email: string, password: string, remember?: boolean) => Promise<void>
  register: (email: string, password: string, displayName?: string) => Promise<void>
  logout: () => void
  loadProfile: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: !!tokenStore.get('accessToken'),
  isLoading: false,

  login: async (email, password, remember = false) => {
    const res = await authApi.login({ email, password })
    tokenStore.set('accessToken', res.accessToken, remember)
    tokenStore.set('refreshToken', res.refreshToken, remember)
    set({ isAuthenticated: true })
    const user = await authApi.getProfile()
    set({ user })
  },

  register: async (email, password, displayName) => {
    const res = await authApi.register({ email, password, displayName })
    // 注册默认记住（和登录行为一致）
    tokenStore.set('accessToken', res.accessToken, true)
    tokenStore.set('refreshToken', res.refreshToken, true)
    set({ isAuthenticated: true })
    const user = await authApi.getProfile()
    set({ user })
  },

  logout: () => {
    tokenStore.remove('accessToken')
    tokenStore.remove('refreshToken')
    set({ user: null, isAuthenticated: false })
  },

  loadProfile: async () => {
    set({ isLoading: true })
    try {
      const user = await authApi.getProfile()
      set({ user, isAuthenticated: true })
    } catch {
      tokenStore.remove('accessToken')
      tokenStore.remove('refreshToken')
      set({ user: null, isAuthenticated: false })
    } finally {
      set({ isLoading: false })
    }
  },
}))
