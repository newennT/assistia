import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { changeTicketPriority, changeTicketStatus, createTicket, getTicket, getTicketAnalyses, getTickets, requestTicketAnalysis, updateTicket } from '../tickets'

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

const analysis = {
  id: 5, ticketId: 7, summary: 'Incident sur VPN.', suggestedCategory: 'VPN', suggestedPriority: 'HIGH',
  possibleCauses: ['DNS'], suggestedActions: ['Tester la résolution DNS'], confidence: 0.72,
  modelUsed: 'gemini-3.8-flash', createdAt: '2026-10-26T12:00:00',
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

  it('creates a ticket with only its accepted editable fields and validates the response', async () => {
    apiRequest.mockResolvedValue(ticket)
    await expect(createTicket({
      title: 'Accès VPN indisponible',
      description: 'Connexion impossible',
      category: 'VPN',
    })).resolves.toEqual(ticket)
    expect(apiRequest).toHaveBeenCalledWith('/api/tickets', {
      method: 'POST',
      body: {
        title: 'Accès VPN indisponible',
        description: 'Connexion impossible',
        category: 'VPN',
      },
    })
  })

  it('rejects a malformed created ticket response', async () => {
    apiRequest.mockResolvedValue({ id: 8, title: 'Incomplete' })
    await expect(createTicket({ title: 'Titre', description: 'Description', category: 'GENERAL' }))
      .rejects.toMatchObject({ name: 'ApiError', status: 502 })
  })

  it('updates ticket information through the existing PUT endpoint', async () => {
    apiRequest.mockResolvedValue(ticket)
    await expect(updateTicket(7, { title: 'Titre', description: 'Détail', category: 'VPN' })).resolves.toEqual(ticket)
    expect(apiRequest).toHaveBeenCalledWith('/api/tickets/7', {
      method: 'PUT', body: { title: 'Titre', description: 'Détail', category: 'VPN' },
    })
  })

  it('changes status and priority using the backend enum values', async () => {
    apiRequest.mockResolvedValue(ticket)
    await changeTicketStatus(7, 'IN_PROGRESS')
    await changeTicketPriority(7, 'CRITICAL')
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/tickets/7/status', { method: 'PATCH', body: { status: 'IN_PROGRESS' } })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/tickets/7/priority', { method: 'PATCH', body: { priority: 'CRITICAL' } })
  })

  it('requests an analysis and loads its stored history', async () => {
    apiRequest.mockResolvedValueOnce(analysis).mockResolvedValueOnce([analysis])
    await expect(requestTicketAnalysis(7)).resolves.toEqual(analysis)
    await expect(getTicketAnalyses(7)).resolves.toEqual([analysis])
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/tickets/7/analysis', { method: 'POST' })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/tickets/7/analysis')
  })

  it('rejects invalid analysis values and malformed histories', async () => {
    apiRequest.mockResolvedValueOnce({ ...analysis, suggestedPriority: 'URGENT' })
    await expect(requestTicketAnalysis(7)).rejects.toMatchObject({ status: 502 })
    apiRequest.mockResolvedValueOnce([analysis, { ...analysis, confidence: 2 }])
    await expect(getTicketAnalyses(7)).rejects.toMatchObject({ status: 502 })
  })
})
