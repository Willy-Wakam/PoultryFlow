import { useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import {
  AuthenticationContext,
  type AuthenticationContextValue,
} from './AuthContext'
import { keycloakClient } from './keycloak'

type AuthenticationState = Pick<
  AuthenticationContextValue,
  'status' | 'username' | 'roles'
>

export function AuthProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<AuthenticationState>({
    status: 'initializing',
    roles: [],
  })

  useEffect(() => {
    let active = true

    const showCurrentState = () => {
      if (!active) {
        return
      }

      setState(
        keycloakClient.isAuthenticated()
          ? {
              status: 'authenticated',
              username: keycloakClient.username(),
              roles: keycloakClient.roles(),
            }
          : { status: 'unauthenticated', roles: [] },
      )
    }

    const showAuthenticationError = () => {
      keycloakClient.clearToken()
      if (active) {
        setState({ status: 'error', roles: [] })
      }
    }

    const refreshExpiredToken = () => {
      void keycloakClient
        .refreshToken()
        .then(showCurrentState)
        .catch(showAuthenticationError)
    }

    keycloakClient.setEvents({
      onAuthenticated: showCurrentState,
      onAuthenticationError: showAuthenticationError,
      onLoggedOut: showCurrentState,
      onTokenExpired: refreshExpiredToken,
    })

    void keycloakClient
      .initialize()
      .then(showCurrentState)
      .catch(showAuthenticationError)

    return () => {
      active = false
    }
  }, [])

  const value = useMemo<AuthenticationContextValue>(
    () => ({
      ...state,
      async login() {
        setState({ status: 'initializing', roles: [] })
        try {
          await keycloakClient.login()
        } catch {
          setState({ status: 'error', roles: [] })
        }
      },
      async logout() {
        setState({ status: 'unauthenticated', roles: [] })
        try {
          await keycloakClient.logout()
        } catch {
          keycloakClient.clearToken()
          setState({ status: 'error', roles: [] })
        }
      },
      hasRole(role) {
        return state.roles.includes(role)
      },
      hasAnyRole(roles) {
        return roles.some((role) => state.roles.includes(role))
      },
    }),
    [state],
  )

  return (
    <AuthenticationContext.Provider value={value}>
      {children}
    </AuthenticationContext.Provider>
  )
}
