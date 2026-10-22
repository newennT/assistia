import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, ApiError, getCsrfToken, isApiErrorStatus, resetCsrfToken } from '../api'

describe('API client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    resetCsrfToken()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it('returns JSON and includes session credentials', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"ok":true}', {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))

    await expect(apiRequest<{ ok: boolean }>('/api/example')).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/example'), expect.objectContaining({ credentials: 'include' }))
  })

  it('throws a typed error using the backend message on HTTP errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"message":"Accès refusé."}', {
      status: 403,
      headers: { 'Content-Type': 'application/json' },
    }))

    await expect(apiRequest('/api/private')).rejects.toMatchObject({
      name: 'ApiError', status: 403, message: 'Accès refusé.',
    })
  })

  it('recognizes expired sessions from unauthorized API errors', () => {
    expect(isApiErrorStatus(new ApiError('Authentification requise.', 401), 401)).toBe(true)
    expect(isApiErrorStatus({ status: 401 }, 401)).toBe(true)
    expect(isApiErrorStatus(new ApiError('Accès refusé.', 403), 401)).toBe(false)
  })

  it('sends the CSRF token from the API response for state-changing requests', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('{"headerName":"X-XSRF-TOKEN","token":"test-token"}', {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await getCsrfToken(true)
    await apiRequest('/api/auth/logout', { method: 'POST' })
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('test-token')
  })
})
