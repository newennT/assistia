import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TicketCreateView from '../TicketCreateView.vue'
import { ApiError } from '@/services/api'
import { authState } from '@/services/auth'
import { createTicket } from '@/services/tickets'
import type { Ticket } from '@/types/ticket'

vi.mock('@/services/tickets', () => ({
  createTicket: vi.fn<(input: { title: string; description: string; category: string }) => Promise<Ticket>>(),
}))

const ticket: Ticket = {
  id: 42,
  title: 'Accès VPN indisponible',
  description: 'Connexion impossible depuis ce matin.',
  status: 'OPEN',
  priority: 'MEDIUM',
  category: 'VPN',
  creatorId: 8,
  assigneeId: null,
  createdAt: '2026-10-25T09:00:00',
  updatedAt: '2026-10-25T09:00:00',
}

async function mountCreateView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/tickets/new', name: 'ticket-new', component: TicketCreateView },
      { path: '/tickets/:id', name: 'ticket-detail', component: { template: '<div>Ticket detail</div>' } },
      { path: '/login', name: 'login', component: { template: '<div>Connexion</div>' } },
      { path: '/tickets', name: 'tickets', component: { template: '<div>Tickets</div>' } },
    ],
  })
  await router.push('/tickets/new')
  await router.isReady()
  const wrapper = mount(TicketCreateView, { global: { plugins: [router] } })
  return { wrapper, router }
}

async function fillForm(wrapper: VueWrapper) {
  await wrapper.get('#ticket-title').setValue('  Accès VPN indisponible  ')
  await wrapper.get('#ticket-category').setValue('VPN')
  await wrapper.get('#ticket-description').setValue('  Connexion impossible depuis ce matin.  ')
}

describe('TicketCreateView', () => {
  beforeEach(() => {
    vi.mocked(createTicket).mockReset()
    authState.user = {
      id: 8, email: 'user@example.com', firstName: 'Alex', lastName: 'User', role: 'USER',
      department: null, createdAt: '2026-01-01T00:00:00',
    }
  })

  afterEach(() => vi.restoreAllMocks())

  it('validates required fields and does not send an incomplete form', async () => {
    const { wrapper } = await mountCreateView()
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('#ticket-title').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#ticket-description').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#ticket-category').attributes('aria-invalid')).toBe('true')
    expect(wrapper.findAll('[role="alert"]').length).toBe(3)
    expect(createTicket).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('submits trimmed fields and navigates to the returned ticket id', async () => {
    vi.mocked(createTicket).mockResolvedValue(ticket)
    const { wrapper, router } = await mountCreateView()
    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createTicket).toHaveBeenCalledWith({
      title: 'Accès VPN indisponible',
      description: 'Connexion impossible depuis ce matin.',
      category: 'VPN',
    })
    expect(router.currentRoute.value.fullPath).toBe('/tickets/42')
    wrapper.unmount()
  })

  it('shows HTTP and network failures without navigating', async () => {
    vi.mocked(createTicket).mockRejectedValueOnce(new ApiError('La catégorie est invalide.', 400))
      .mockRejectedValueOnce(new ApiError('Impossible de joindre le serveur. Réessayez.', 0))
    const { wrapper, router } = await mountCreateView()
    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('La catégorie est invalide.')
    expect(router.currentRoute.value.fullPath).toBe('/tickets/new')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Impossible de joindre le serveur')
    expect(router.currentRoute.value.fullPath).toBe('/tickets/new')
    wrapper.unmount()
  })

  it('clears the expired session and redirects to login on a 401', async () => {
    vi.mocked(createTicket).mockRejectedValue(new ApiError('Authentification requise.', 401))
    const { wrapper, router } = await mountCreateView()
    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(authState.user).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.expired).toBe('true')
    wrapper.unmount()
  })

  it('prevents duplicate submissions while the request is pending', async () => {
    let resolveRequest!: (created: Ticket) => void
    vi.mocked(createTicket).mockReturnValue(new Promise((resolve) => { resolveRequest = resolve }))
    const { wrapper } = await mountCreateView()
    await fillForm(wrapper)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(createTicket).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    resolveRequest(ticket)
    await flushPromises()
    wrapper.unmount()
  })
})
