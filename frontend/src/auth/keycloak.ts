import Keycloak from 'keycloak-js'

type AuthenticationEvents = {
  onAuthenticated: () => void
  onAuthenticationError: () => void
  onLoggedOut: () => void
  onTokenExpired: () => void
}

const applicationRoot = `${window.location.origin}/`

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

  login(): Promise<void> {
    return keycloak.login({ redirectUri: applicationRoot })
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
