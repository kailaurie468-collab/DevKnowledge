import { create } from 'zustand'
import type { User } from '@/types/api'
import { authApi } from '@/api/auth'

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName?: string) => Promise<void>
  logout: () => void
  loadProfile: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: !!localStorage.getItem('accessToken'),
  isLoading: false,

  login: async (email, password) => {
    const res = await authApi.login({ email, password })
    localStorage.setItem('accessToken', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    set({ isAuthenticated: true })
    const user = await authApi.getProfile()
    set({ user })
  },

  register: async (email, password, displayName) => {
    const res = await authApi.register({ email, password, displayName })
    localStorage.setItem('accessToken', res.accessToken)
    localStorage.setItem('refreshToken', res.refreshToken)
    set({ isAuthenticated: true })
    const user = await authApi.getProfile()
    set({ user })
  },

  logout: () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    set({ user: null, isAuthenticated: false })
  },

  loadProfile: async () => {
    set({ isLoading: true })
    try {
      const user = await authApi.getProfile()
      set({ user, isAuthenticated: true })
    } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      set({ user: null, isAuthenticated: false })
    } finally {
      set({ isLoading: false })
    }
  },
}))
