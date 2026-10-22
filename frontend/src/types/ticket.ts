export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'PENDING' | 'RESOLVED' | 'CLOSED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type TicketCategory =
  | 'WINDOWS' | 'LINUX' | 'DNS' | 'DHCP' | 'VPN' | 'NETWORK' | 'PRINTER'
  | 'USER_ACCOUNT' | 'PERMISSIONS' | 'M365' | 'BUSINESS_APP' | 'GENERAL' | 'OTHER'

export interface Ticket {
  id: number
  title: string
  description: string
  status: TicketStatus
  priority: TicketPriority
  category: TicketCategory
  creatorId: number
  assigneeId: number | null
  createdAt: string
  updatedAt: string
}

export interface TicketFilters {
  status?: TicketStatus
  priority?: TicketPriority
}
