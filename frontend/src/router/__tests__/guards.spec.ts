import { afterEach, describe, expect, it, vi } from 'vitest'
import router from '../index'
import { authState } from '@/services/auth'

describe('navigation guards', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    authState.user = null
    authState.ready = true
  })

  it('redirects unauthenticated visitors to login and preserves the destination', async () => {
    authState.ready = true
    authState.user = null
    await router.push('/tickets')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/tickets')
  })

  it('returns an authenticated user without the admin role to the dashboard', async () => {
    authState.ready = true
    authState.user = {
      id: 2, email: 'user@example.com', firstName: 'Alex', lastName: 'User', role: 'USER',
      department: null, createdAt: '2026-01-01T00:00:00',
    }
    await router.push('/admin')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it.each(['USER', 'TECHNICIAN', 'ADMIN'] as const)('%s can access the dashboard and ticket list', async (role) => {
    authState.ready = true
    authState.user = {
      id: 2, email: 'user@example.com', firstName: 'Alex', lastName: 'User', role,
      department: null, createdAt: '2026-01-01T00:00:00',
    }
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('home')
    await router.push('/tickets')
    expect(router.currentRoute.value.name).toBe('tickets')
  })

  it('allows a regular user to reach a ticket detail route for backend-authorized lookup', async () => {
    authState.ready = true
    authState.user = {
      id: 2, email: 'user@example.com', firstName: 'Alex', lastName: 'User', role: 'USER',
      department: null, createdAt: '2026-01-01T00:00:00',
    }
    await router.push('/tickets/14')
    expect(router.currentRoute.value.name).toBe('ticket-detail')
  })
})
