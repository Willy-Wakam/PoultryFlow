import { useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import {
  AuthenticationContext,
  type AuthenticationContextValue,
} from './AuthContext'
import { keycloakClient } from './keycloak'

type AuthenticationState = Pick<
  AuthenticationContextValue,
  'status' | 'username'
>

export function AuthProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<AuthenticationState>({
    status: 'initializing',
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
            }
          : { status: 'unauthenticated' },
      )
    }

    const showAuthenticationError = () => {
      keycloakClient.clearToken()
      if (active) {
        setState({ status: 'error' })
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
        setState({ status: 'initializing' })
        try {
          await keycloakClient.login()
        } catch {
          setState({ status: 'error' })
        }
      },
      async logout() {
        setState({ status: 'unauthenticated' })
        try {
          await keycloakClient.logout()
        } catch {
          keycloakClient.clearToken()
          setState({ status: 'error' })
        }
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
