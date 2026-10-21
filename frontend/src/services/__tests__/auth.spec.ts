import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, resetCsrfToken } from '../api'
import { authState, initializeSession, login, logout } from '../auth'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const user = {
  id: 7, email: 'sam@example.com', firstName: 'Sam', lastName: 'Lee', role: 'TECHNICIAN',
  department: 'IT', createdAt: '2026-01-01T10:00:00',
}

describe('authentication session', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    resetCsrfToken()
    authState.user = null
    authState.ready = false
    authState.loading = false
  })

  it('treats an absent server session as unauthenticated', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ message: 'Authentification requise.' }, 401))
    await initializeSession()
    expect(authState.user).toBeNull()
    expect(authState.ready).toBe(true)
  })

  it('logs in, reloads the current user and logs out through the server', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(user)) // initial CSRF response
      .mockResolvedValueOnce(jsonResponse(user)) // login response
      .mockResolvedValueOnce(jsonResponse(user)) // refreshed CSRF response
      .mockResolvedValueOnce(jsonResponse(user)) // /me
      .mockResolvedValueOnce(new Response(null, { status: 204 })) // logout CSRF fetch cached
    await expect(login('sam@example.com', 'never-stored')).resolves.toEqual(user)
    expect(authState.user).toEqual(user)

    await logout()
    expect(authState.user).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(5)
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/api/auth/logout'))).toBe(true)
  })

  it('surfaces login HTTP failures without creating a local session', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token' }))
      .mockResolvedValueOnce(jsonResponse({ message: 'Identifiants invalides.' }, 401))
    await expect(login('sam@example.com', 'incorrect')).rejects.toBeInstanceOf(ApiError)
    expect(authState.user).toBeNull()
  })
})
