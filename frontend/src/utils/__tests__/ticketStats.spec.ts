import { describe, expect, it } from 'vitest'
import { getTicketStats, recentTickets } from '../ticketStats'
import type { Ticket } from '@/types/ticket'

const makeTicket = (id: number, status: Ticket['status'], priority: Ticket['priority'], updatedAt: string): Ticket => ({
  id,
  title: `Ticket ${id}`,
  description: 'Description',
  status,
  priority,
  category: 'GENERAL',
  creatorId: 1,
  assigneeId: null,
  createdAt: '2026-10-20T09:00:00',
  updatedAt,
})

describe('ticket dashboard statistics', () => {
  it('counts exact backend statuses and priorities', () => {
    const tickets = [
      makeTicket(1, 'OPEN', 'CRITICAL', '2026-10-20T09:00:00'),
      makeTicket(2, 'IN_PROGRESS', 'HIGH', '2026-10-21T09:00:00'),
      makeTicket(3, 'RESOLVED', 'HIGH', '2026-10-22T09:00:00'),
      makeTicket(4, 'PENDING', 'LOW', '2026-10-19T09:00:00'),
      makeTicket(5, 'CLOSED', 'MEDIUM', '2026-10-18T09:00:00'),
    ]
    expect(getTicketStats(tickets)).toEqual({
      open: 1,
      inProgress: 1,
      resolved: 1,
      priorities: { CRITICAL: 1, HIGH: 2, MEDIUM: 1, LOW: 1 },
    })
  })

  it('returns the most recently updated tickets without changing the input', () => {
    const tickets = [
      makeTicket(1, 'OPEN', 'LOW', '2026-10-20T09:00:00'),
      makeTicket(2, 'OPEN', 'LOW', '2026-10-22T09:00:00'),
      makeTicket(3, 'OPEN', 'LOW', '2026-10-21T09:00:00'),
    ]
    expect(recentTickets(tickets, 2).map(({ id }) => id)).toEqual([2, 3])
    expect(tickets.map(({ id }) => id)).toEqual([1, 2, 3])
  })
})
