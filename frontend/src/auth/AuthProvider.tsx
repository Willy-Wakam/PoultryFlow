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

      if (keycloakClient.isAuthenticated()) {
        keycloakClient.synchronizeAuthorizationSnapshot()
        setState({
          status: 'authenticated',
          username: keycloakClient.username(),
          roles: keycloakClient.roles(),
        })
      } else {
        setState({ status: 'unauthenticated', roles: [] })
      }
    }

    const showAuthenticationError = () => {
      keycloakClient.clearLocalSession()
      if (active) {
        setState({ status: 'error', roles: [] })
      }
    }

    const showSessionExpired = () => {
      if (active) {
        setState({ status: 'expired', roles: [] })
      }
    }

    const refreshExpiredToken = () => {
      void keycloakClient
        .refreshToken()
        .then(() => {
          if (keycloakClient.isAuthenticated()) {
            showCurrentState()
          } else {
            keycloakClient.expireSession()
            showSessionExpired()
          }
        })
        .catch(() => {
          keycloakClient.expireSession()
          showSessionExpired()
        })
    }

    keycloakClient.setEvents({
      onAuthenticated: showCurrentState,
      onAuthenticationError: showAuthenticationError,
      onLoggedOut: showCurrentState,
      onSessionExpired: showSessionExpired,
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
      async recoverCredentials() {
        setState({ status: 'initializing', roles: [] })
        try {
          await keycloakClient.recoverCredentials()
        } catch {
          setState({ status: 'error', roles: [] })
        }
      },
      async logout() {
        setState({ status: 'unauthenticated', roles: [] })
        try {
          await keycloakClient.logout()
        } catch {
          keycloakClient.clearLocalSession()
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
