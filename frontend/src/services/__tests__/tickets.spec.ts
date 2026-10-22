import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { getTicket, getTickets } from '../tickets'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn<(path: string, options?: unknown) => Promise<unknown>>() }))
vi.mock('../api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api')>()),
  apiRequest,
}))

const ticket = {
  id: 7,
  title: 'Accès VPN indisponible',
  description: 'Connexion impossible',
  status: 'IN_PROGRESS',
  priority: 'HIGH',
  category: 'VPN',
  creatorId: 2,
  assigneeId: 4,
  createdAt: '2026-10-20T09:00:00',
  updatedAt: '2026-10-21T10:30:00',
}

describe('ticket service', () => {
  beforeEach(() => apiRequest.mockReset())

  it('loads and validates the ticket array returned by the API', async () => {
    apiRequest.mockResolvedValue([ticket])
    await expect(getTickets()).resolves.toEqual([ticket])
    expect(apiRequest).toHaveBeenCalledWith('/api/tickets')
  })

  it('sends backend status and priority filters as query parameters', async () => {
    apiRequest.mockResolvedValue([])
    await getTickets({ status: 'IN_PROGRESS', priority: 'HIGH' })
    expect(apiRequest).toHaveBeenCalledWith('/api/tickets?status=IN_PROGRESS&priority=HIGH')
  })

  it('rejects unexpected response shapes and enum values', async () => {
    apiRequest.mockResolvedValueOnce({ content: [ticket] })
    await expect(getTickets()).rejects.toMatchObject({ name: 'ApiError', status: 502 })
    apiRequest.mockResolvedValueOnce([{ ...ticket, status: 'ACTIVE' }])
    await expect(getTickets()).rejects.toMatchObject({ name: 'ApiError', status: 502 })
  })

  it('loads a ticket by id and rejects invalid ids', async () => {
    apiRequest.mockResolvedValue(ticket)
    await expect(getTicket(7)).resolves.toEqual(ticket)
    expect(apiRequest).toHaveBeenCalledWith('/api/tickets/7')
    await expect(getTicket(0)).rejects.toBeInstanceOf(ApiError)
  })
})
