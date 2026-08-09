import { createContext } from 'react'
import type { PoultryFlowRole } from './roles'

export type AuthenticationStatus =
  'initializing' | 'unauthenticated' | 'authenticated' | 'error'

export type AuthenticationContextValue = {
  status: AuthenticationStatus
  username?: string
  roles: readonly PoultryFlowRole[]
  hasRole: (role: PoultryFlowRole) => boolean
  hasAnyRole: (roles: readonly PoultryFlowRole[]) => boolean
  login: () => Promise<void>
  logout: () => Promise<void>
}

export const AuthenticationContext =
  createContext<AuthenticationContextValue | null>(null)
