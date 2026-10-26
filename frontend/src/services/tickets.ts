import { apiRequest, ApiError } from './api'
import type { Ticket, TicketAnalysis, TicketCategory, TicketFilters } from '@/types/ticket'

export interface CreateTicketInput {
  title: string
  description: string
  category: TicketCategory
}

export type UpdateTicketInput = CreateTicketInput

const statuses = ['OPEN', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED']
const priorities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const categories = ['WINDOWS', 'LINUX', 'DNS', 'DHCP', 'VPN', 'NETWORK', 'PRINTER', 'USER_ACCOUNT', 'PERMISSIONS', 'M365', 'BUSINESS_APP', 'GENERAL', 'OTHER']

function isTicket(value: unknown): value is Ticket {
  if (!value || typeof value !== 'object') return false
  const ticket = value as Record<string, unknown>
  return Number.isInteger(ticket.id)
    && typeof ticket.title === 'string'
    && typeof ticket.description === 'string'
    && statuses.includes(String(ticket.status))
    && priorities.includes(String(ticket.priority))
    && categories.includes(String(ticket.category))
    && Number.isInteger(ticket.creatorId)
    && (ticket.assigneeId === null || Number.isInteger(ticket.assigneeId))
    && typeof ticket.createdAt === 'string'
    && typeof ticket.updatedAt === 'string'
}

function validateTicket(value: unknown): Ticket {
  if (!isTicket(value)) throw new ApiError('Le serveur a renvoyé un ticket dans un format inattendu.', 502)
  return value
}

function isTicketAnalysis(value: unknown): value is TicketAnalysis {
  if (!value || typeof value !== 'object') return false
  const analysis = value as Record<string, unknown>
  return Number.isInteger(analysis.id)
    && Number.isInteger(analysis.ticketId)
    && typeof analysis.summary === 'string'
    && categories.includes(String(analysis.suggestedCategory))
    && priorities.includes(String(analysis.suggestedPriority))
    && Array.isArray(analysis.possibleCauses) && analysis.possibleCauses.every((item) => typeof item === 'string')
    && Array.isArray(analysis.suggestedActions) && analysis.suggestedActions.every((item) => typeof item === 'string')
    && typeof analysis.confidence === 'number' && analysis.confidence >= 0 && analysis.confidence <= 1
    && typeof analysis.modelUsed === 'string'
    && typeof analysis.createdAt === 'string'
}

function validateAnalysis(value: unknown): TicketAnalysis {
  if (!isTicketAnalysis(value)) throw new ApiError('Le serveur a renvoyé une analyse dans un format inattendu.', 502)
  return value
}

export async function getTickets(filters: TicketFilters = {}): Promise<Ticket[]> {
  const query = new URLSearchParams()
  if (filters.status) query.set('status', filters.status)
  if (filters.priority) query.set('priority', filters.priority)
  const suffix = query.size ? `?${query.toString()}` : ''
  const data: unknown = await apiRequest(`/api/tickets${suffix}`)
  if (!Array.isArray(data)) throw new ApiError('Le serveur a renvoyé une liste de tickets dans un format inattendu.', 502)
  return data.map(validateTicket)
}

export async function getTicket(id: number): Promise<Ticket> {
  if (!Number.isInteger(id) || id <= 0) throw new ApiError('Identifiant de ticket invalide.', 400)
  const data: unknown = await apiRequest(`/api/tickets/${id}`)
  return validateTicket(data)
}

export async function createTicket(input: CreateTicketInput): Promise<Ticket> {
  const data: unknown = await apiRequest('/api/tickets', {
    method: 'POST',
    body: {
      title: input.title,
      description: input.description,
      category: input.category,
    },
  })
  return validateTicket(data)
}

export async function updateTicket(id: number, input: UpdateTicketInput): Promise<Ticket> {
  if (!Number.isInteger(id) || id <= 0) throw new ApiError('Identifiant de ticket invalide.', 400)
  const data: unknown = await apiRequest(`/api/tickets/${id}`, {
    method: 'PUT',
    body: { title: input.title, description: input.description, category: input.category },
  })
  return validateTicket(data)
}

export async function changeTicketStatus(id: number, status: Ticket['status']): Promise<Ticket> {
  if (!Number.isInteger(id) || id <= 0) throw new ApiError('Identifiant de ticket invalide.', 400)
  const data: unknown = await apiRequest(`/api/tickets/${id}/status`, { method: 'PATCH', body: { status } })
  return validateTicket(data)
}

export async function changeTicketPriority(id: number, priority: Ticket['priority']): Promise<Ticket> {
  if (!Number.isInteger(id) || id <= 0) throw new ApiError('Identifiant de ticket invalide.', 400)
  const data: unknown = await apiRequest(`/api/tickets/${id}/priority`, { method: 'PATCH', body: { priority } })
  return validateTicket(data)
}

export async function requestTicketAnalysis(id: number): Promise<TicketAnalysis> {
  if (!Number.isInteger(id) || id <= 0) throw new ApiError('Identifiant de ticket invalide.', 400)
  const data: unknown = await apiRequest(`/api/tickets/${id}/analysis`, { method: 'POST' })
  return validateAnalysis(data)
}

export async function getTicketAnalyses(id: number): Promise<TicketAnalysis[]> {
  if (!Number.isInteger(id) || id <= 0) throw new ApiError('Identifiant de ticket invalide.', 400)
  const data: unknown = await apiRequest(`/api/tickets/${id}/analysis`)
  if (!Array.isArray(data)) throw new ApiError('Le serveur a renvoyé un historique dans un format inattendu.', 502)
  return data.map(validateAnalysis)
}
