import { computed, reactive } from 'vue'
import { ApiError, apiRequest, getCsrfToken, resetCsrfToken } from './api'
import type { AuthenticatedUser } from '@/types/auth'

const state = reactive({
  user: null as AuthenticatedUser | null,
  ready: false,
  loading: false,
})

let initialization: Promise<void> | null = null

export const authState = state
export const currentUser = computed(() => state.user)

export async function initializeSession(): Promise<void> {
  if (state.ready) return
  if (initialization) return initialization

  initialization = (async () => {
    state.loading = true
    try {
      state.user = await apiRequest<AuthenticatedUser>('/api/auth/me')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        state.user = null
      } else {
        state.user = null
        throw error
      }
    } finally {
      state.ready = true
      state.loading = false
      initialization = null
    }
  })()
  return initialization
}

export async function login(email: string, password: string): Promise<AuthenticatedUser> {
  state.loading = true
  try {
    await getCsrfToken(true)
    await apiRequest<AuthenticatedUser>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    })
    resetCsrfToken()
    await getCsrfToken(true)
    state.user = await apiRequest<AuthenticatedUser>('/api/auth/me')
    state.ready = true
    return state.user
  } finally {
    state.loading = false
  }
}

export async function logout(): Promise<void> {
  await apiRequest<void>('/api/auth/logout', { method: 'POST' })
  state.user = null
  state.ready = true
  resetCsrfToken()
}

export function clearSession(): void {
  state.user = null
  state.ready = true
}
