import Keycloak from 'keycloak-js'
import { extractPoultryFlowRoles } from './roles'

type AuthenticationEvents = {
  onAuthenticated: () => void
  onAuthenticationError: () => void
  onLoggedOut: () => void
  onTokenExpired: () => void
}

const applicationRoot = `${window.location.origin}/`

export function credentialRecoveryUrl(loginUrl: string): string {
  let url: URL

  try {
    url = new URL(loginUrl)
  } catch {
    throw new Error('Unexpected identity service login URL')
  }

  if (
    (url.protocol !== 'http:' && url.protocol !== 'https:') ||
    !url.pathname.endsWith('/auth')
  ) {
    throw new Error('Unexpected identity service login URL')
  }

  url.pathname = `${url.pathname.slice(0, -'/auth'.length)}/forgot-credentials`
  return url.toString()
}

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'poultryflow',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'poultryflow-web',
})

let initialization: Promise<boolean> | undefined

export const keycloakClient = {
  initialize(): Promise<boolean> {
    initialization ??= keycloak.init({
      flow: 'standard',
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      redirectUri: applicationRoot,
      silentCheckSsoFallback: true,
      silentCheckSsoRedirectUri: `${applicationRoot}silent-check-sso.html`,
      useNonce: true,
    })

    return initialization
  },

  isAuthenticated(): boolean {
    return keycloak.authenticated === true
  },

  username(): string | undefined {
    const username = keycloak.tokenParsed?.preferred_username
    return typeof username === 'string' ? username : undefined
  },

  roles() {
    return extractPoultryFlowRoles(keycloak.tokenParsed)
  },

  login(): Promise<void> {
    return keycloak.login({ redirectUri: applicationRoot })
  },

  async recoverCredentials(): Promise<void> {
    const loginUrl = await keycloak.createLoginUrl({
      redirectUri: applicationRoot,
    })
    window.location.assign(credentialRecoveryUrl(loginUrl))
  },

  logout(): Promise<void> {
    return keycloak.logout({ redirectUri: applicationRoot })
  },

  refreshToken(): Promise<boolean> {
    return keycloak.updateToken(30)
  },

  clearToken(): void {
    keycloak.clearToken()
  },

  setEvents(events: AuthenticationEvents): void {
    keycloak.onAuthSuccess = events.onAuthenticated
    keycloak.onAuthError = events.onAuthenticationError
    keycloak.onAuthRefreshError = events.onAuthenticationError
    keycloak.onAuthLogout = events.onLoggedOut
    keycloak.onTokenExpired = events.onTokenExpired
  },
}
