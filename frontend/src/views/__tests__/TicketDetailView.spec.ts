import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TicketDetailView from '../TicketDetailView.vue'
import { ApiError } from '@/services/api'
import { authState } from '@/services/auth'
import { changeTicketPriority, changeTicketStatus, getTicket, updateTicket } from '@/services/tickets'
import type { Ticket } from '@/types/ticket'

vi.mock('@/services/tickets', () => ({
  getTicket: vi.fn<() => Promise<Ticket>>(),
  updateTicket: vi.fn<(id: number, input: unknown) => Promise<Ticket>>(),
  changeTicketStatus: vi.fn<(id: number, status: Ticket['status']) => Promise<Ticket>>(),
  changeTicketPriority: vi.fn<(id: number, priority: Ticket['priority']) => Promise<Ticket>>(),
}))

const ticket: Ticket = {
  id: 42, title: 'Accès VPN', description: 'Connexion impossible', status: 'OPEN', priority: 'MEDIUM',
  category: 'VPN', creatorId: 8, assigneeId: null, createdAt: '2026-10-25T09:00:00', updatedAt: '2026-10-25T09:00:00',
}

async function mountDetail(role: 'USER' | 'TECHNICIAN' | 'ADMIN', userId = 8) {
  authState.user = {
    id: userId, email: 'user@example.com', firstName: 'Alex', lastName: 'User', role,
    department: null, createdAt: '2026-01-01T00:00:00',
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/tickets/:id', name: 'ticket-detail', component: TicketDetailView },
      { path: '/login', name: 'login', component: { template: '<div>Connexion</div>' } },
    ],
  })
  await router.push('/tickets/42')
  await router.isReady()
  const wrapper = mount(TicketDetailView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('TicketDetailView', () => {
  beforeEach(() => {
    vi.mocked(getTicket).mockReset().mockResolvedValue(ticket)
    vi.mocked(updateTicket).mockReset()
    vi.mocked(changeTicketStatus).mockReset()
    vi.mocked(changeTicketPriority).mockReset()
  })
  afterEach(() => vi.restoreAllMocks())

  it('shows editing controls only for the creator and does not let a regular user change category', async () => {
    const { wrapper } = await mountDetail('USER')
    expect(wrapper.text()).toContain('Accès VPN')
    await wrapper.get('button').trigger('click')
    expect(wrapper.find('#edit-title').exists()).toBe(true)
    expect(wrapper.get('#edit-category').attributes('disabled')).toBeDefined()
    expect(wrapper.find('#ticket-status').exists()).toBe(false)
    wrapper.unmount()

    const otherUser = await mountDetail('USER', 99)
    expect(otherUser.wrapper.find('button').exists()).toBe(false)
    otherUser.wrapper.unmount()
  })

  it('allows staff to edit category, change status and priority, then displays returned data', async () => {
    const changed: Ticket = { ...ticket, title: 'Ticket traité', category: 'NETWORK', status: 'IN_PROGRESS', priority: 'HIGH' }
    vi.mocked(updateTicket).mockResolvedValue(changed)
    vi.mocked(changeTicketStatus).mockResolvedValue(changed)
    vi.mocked(changeTicketPriority).mockResolvedValue(changed)
    const { wrapper } = await mountDetail('TECHNICIAN')

    await wrapper.get('#ticket-status').setValue('IN_PROGRESS')
    await flushPromises()
    expect(changeTicketStatus).toHaveBeenCalledWith(42, 'IN_PROGRESS')
    await wrapper.get('#ticket-priority').setValue('HIGH')
    await flushPromises()
    expect(changeTicketPriority).toHaveBeenCalledWith(42, 'HIGH')

    await wrapper.get('button').trigger('click')
    await wrapper.get('#edit-title').setValue('Ticket traité')
    await wrapper.get('#edit-category').setValue('NETWORK')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(updateTicket).toHaveBeenCalledWith(42, { title: 'Ticket traité', description: 'Connexion impossible', category: 'NETWORK' })
    expect(wrapper.text()).toContain('Ticket traité')
    expect(wrapper.text()).toContain('Réseau')
    wrapper.unmount()
  })

  it('restores local edits on cancel and presents server errors while retaining the form', async () => {
    vi.mocked(updateTicket).mockRejectedValue(new ApiError('Titre invalide.', 400))
    const { wrapper } = await mountDetail('ADMIN')
    await wrapper.get('button').trigger('click')
    await wrapper.get('#edit-title').setValue('Modification locale')
    await wrapper.get('button[type="button"]').trigger('click')
    expect(wrapper.text()).toContain('Accès VPN')

    await wrapper.get('button').trigger('click')
    await wrapper.get('#edit-title').setValue('Titre invalide')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Titre invalide.')
    expect(wrapper.find('#edit-title').exists()).toBe(true)
    wrapper.unmount()
  })

  it('shows loading and retry states for a failed read', async () => {
    let finish!: (value: Ticket) => void
    vi.mocked(getTicket).mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const { wrapper } = await mountDetail('USER')
    expect(wrapper.get('[role="status"]').text()).toContain('Chargement')
    finish(ticket)
    await flushPromises()
    expect(wrapper.text()).toContain('Accès VPN')
    wrapper.unmount()
  })
})
