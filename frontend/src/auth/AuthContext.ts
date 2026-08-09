import { createContext } from 'react'

export type AuthenticationStatus =
  'initializing' | 'unauthenticated' | 'authenticated' | 'error'

export type AuthenticationContextValue = {
  status: AuthenticationStatus
  username?: string
  login: () => Promise<void>
  logout: () => Promise<void>
}

export const AuthenticationContext =
  createContext<AuthenticationContextValue | null>(null)
