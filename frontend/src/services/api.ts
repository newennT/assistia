import type { CsrfTokenResponse } from '@/types/auth'

const configuredApiUrl = import.meta.env.VITE_API_URL?.trim()
export const API_BASE_URL = (configuredApiUrl || 'http://localhost:8080').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function isApiErrorStatus(error: unknown, status: number): boolean {
  if (error instanceof ApiError) return error.status === status
  return typeof error === 'object' && error !== null && 'status' in error && error.status === status
}

let csrfToken: CsrfTokenResponse | null = null

function readCookie(name: string): string | null {
  const cookie = document.cookie
    .split('; ')
    .find((item) => item.startsWith(`${name}=`))
  return cookie ? decodeURIComponent(cookie.slice(name.length + 1)) : null
}

export function resetCsrfToken(): void {
  csrfToken = null
}

export async function getCsrfToken(forceRefresh = false): Promise<CsrfTokenResponse> {
  if (!forceRefresh && csrfToken) return csrfToken

  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: 'include' })
  if (!response.ok) throw await createApiError(response)

  const body = (await response.json()) as CsrfTokenResponse
  const cookieToken = readCookie('XSRF-TOKEN')
  csrfToken = {
    headerName: body.headerName,
    // The JSON value is Spring's XOR-masked request token; the cookie holds the raw token.
    token: cookieToken ?? body.token,
  }
  return csrfToken
}

async function createApiError(response: Response): Promise<ApiError> {
  let message = `La requête a échoué (HTTP ${response.status}).`
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
      message = body.message
    } else if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
      message = body.detail
    }
  } catch {
    // Keep a useful status-based message when the response is not JSON.
  }
  return new ApiError(message, response.status)
}

interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  let body: BodyInit | undefined

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(options.body)
  }

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await getCsrfToken()
    headers.set(csrf.headerName, csrf.token)
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      method,
      headers,
      body,
      credentials: 'include',
    })
  } catch {
    throw new ApiError('Impossible de joindre le serveur. Vérifiez votre connexion puis réessayez.', 0)
  }

  if (!response.ok) throw await createApiError(response)
  if (response.status === 204) return undefined as T

  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) return (await response.json()) as T
  return undefined as T
}
