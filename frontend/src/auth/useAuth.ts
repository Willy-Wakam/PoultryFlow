import { useContext } from 'react'
import { AuthenticationContext } from './AuthContext'

export function useAuth() {
  const authentication = useContext(AuthenticationContext)

  if (authentication === null) {
    throw new Error('useAuth must be used within AuthProvider')
  }

  return authentication
}
