export type UserRole = 'USER' | 'TECHNICIAN' | 'ADMIN'

export interface AuthenticatedUser {
  id: number
  email: string
  firstName: string
  lastName: string
  role: UserRole
  department: string | null
  createdAt: string
}

export interface CsrfTokenResponse {
  headerName: string
  token: string
}
