import { apiRequest, ApiError } from './api'
import type { Ticket, TicketFilters } from '@/types/ticket'

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
