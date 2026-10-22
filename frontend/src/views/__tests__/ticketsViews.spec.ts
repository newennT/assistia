import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Component } from 'vue'
import type { Ticket, TicketFilters } from '@/types/ticket'
import TicketsView from '../TicketsView.vue'
import HomeView from '../HomeView.vue'
import { getTickets } from '@/services/tickets'

vi.mock('@/services/tickets', () => ({ getTickets: vi.fn<(filters?: TicketFilters) => Promise<Ticket[]>>() }))

const ticket: Ticket = {
  id: 14,
  title: 'Impossible de se connecter au VPN',
  description: 'La connexion est interrompue.',
  status: 'OPEN',
  priority: 'HIGH',
  category: 'VPN',
  creatorId: 1,
  assigneeId: null,
  createdAt: '2026-10-20T09:00:00',
  updatedAt: '2026-10-21T10:30:00',
}

function mountView(component: Component, path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path, component: { template: '<div />' } }],
  })
  return router.push(path).then(async () => {
    await router.isReady()
    const wrapper = mount(component, {
      global: {
        plugins: [router],
        stubs: {
          RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
        },
      },
    })
    return { wrapper, router }
  })
}

describe('ticket views', () => {
  beforeEach(() => vi.mocked(getTickets).mockReset())
  afterEach(() => vi.restoreAllMocks())

  it('shows a loading state before the list request finishes', async () => {
    let resolveRequest!: (tickets: Ticket[]) => void
    vi.mocked(getTickets).mockReturnValue(new Promise((resolve) => { resolveRequest = resolve }))
    const { wrapper } = await mountView(TicketsView, '/tickets')
    expect(wrapper.get('[role="status"]').text()).toContain('Chargement des tickets')
    resolveRequest([])
    await flushPromises()
    wrapper.unmount()
  })

  it('renders the empty state and explains empty filtered results', async () => {
    vi.mocked(getTickets).mockResolvedValue([])
    const { wrapper } = await mountView(TicketsView, '/tickets')
    await flushPromises()
    expect(wrapper.text()).toContain('Aucun ticket')
    await wrapper.get('[aria-label="Filtrer par statut"]').setValue('OPEN')
    await flushPromises()
    expect(wrapper.text()).toContain('Aucun résultat')
    wrapper.unmount()
  })

  it('shows a readable error and allows retrying', async () => {
    vi.mocked(getTickets).mockRejectedValueOnce(new Error('Serveur indisponible')).mockResolvedValueOnce([ticket])
    const { wrapper } = await mountView(TicketsView, '/tickets')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Serveur indisponible')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Impossible de se connecter au VPN')
    wrapper.unmount()
  })

  it('renders ticket fields and fetches again when filters change', async () => {
    vi.mocked(getTickets).mockResolvedValue([ticket])
    const { wrapper } = await mountView(TicketsView, '/tickets')
    await flushPromises()
    expect(wrapper.text()).toContain('Impossible de se connecter au VPN')
    expect(wrapper.text()).toContain('VPN')
    expect(wrapper.text()).toContain('Haute')
    await wrapper.get('[aria-label="Filtrer par statut"]').setValue('OPEN')
    await wrapper.get('[aria-label="Filtrer par priorité"]').setValue('HIGH')
    await flushPromises()
    expect(getTickets).toHaveBeenLastCalledWith({ status: 'OPEN', priority: 'HIGH' })
    expect(wrapper.find('a[href="/tickets/14"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('loads dashboard metrics and recent tickets from the API data', async () => {
    vi.mocked(getTickets).mockResolvedValue([ticket, { ...ticket, id: 15, title: 'Ticket récent', status: 'RESOLVED', updatedAt: '2026-10-22T10:30:00' }])
    const { wrapper } = await mountView(HomeView, '/')
    await flushPromises()
    expect(wrapper.text()).toContain('Ouverts')
    expect(wrapper.text()).toContain('En cours')
    expect(wrapper.text()).toContain('Résolus')
    expect(wrapper.text()).toContain('Ticket récent')
    expect(wrapper.text()).toContain('2 tickets dans votre périmètre')
    wrapper.unmount()
  })

})
