import { api } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '@/types/api'

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<AuthResponse>('/auth/login', data),

  register: (data: RegisterRequest) =>
    api.post<AuthResponse>('/auth/register', data),

  refresh: (refreshToken: string) =>
    api.post<AuthResponse>('/auth/refresh', { refreshToken }),

  getProfile: () =>
    api.get<User>('/user/profile'),
}
