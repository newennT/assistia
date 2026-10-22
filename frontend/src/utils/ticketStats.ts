import type { Ticket, TicketPriority } from '@/types/ticket'

export const ticketPriorities: TicketPriority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export function getTicketStats(tickets: Ticket[]) {
  return {
    open: tickets.filter((ticket) => ticket.status === 'OPEN').length,
    inProgress: tickets.filter((ticket) => ticket.status === 'IN_PROGRESS').length,
    resolved: tickets.filter((ticket) => ticket.status === 'RESOLVED').length,
    priorities: Object.fromEntries(
      ticketPriorities.map((priority) => [priority, tickets.filter((ticket) => ticket.priority === priority).length]),
    ) as Record<TicketPriority, number>,
  }
}

export function recentTickets(tickets: Ticket[], count = 5): Ticket[] {
  return [...tickets]
    .sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
    .slice(0, count)
}
