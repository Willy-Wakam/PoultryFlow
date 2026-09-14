import Keycloak from 'keycloak-js'
import {
  clearOfflineAuthorization,
  recordOfflineAuthorization,
} from './offlineAuthorization'
import { extractPoultryFlowRoles } from './roles'

type AuthenticationEvents = {
  onAuthenticated: () => void
  onAuthenticationError: () => void
  onLoggedOut: () => void
  onSessionExpired: () => void
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
let authenticationEvents: AuthenticationEvents | undefined
let refreshInFlight: Promise<boolean> | undefined
let suppressAdapterLogout = false
let sessionExpired = false

function hasUsableAccessToken(): boolean {
  const token = keycloak.token
  const expiresAt = keycloak.tokenParsed?.exp
  const timeSkew = keycloak.timeSkew ?? 0

  return (
    keycloak.authenticated === true &&
    typeof token === 'string' &&
    token.length > 0 &&
    typeof expiresAt === 'number' &&
    expiresAt + timeSkew > Math.ceil(Date.now() / 1000)
  )
}

function synchronizeOfflineAuthorization(): void {
  const subject = keycloak.tokenParsed?.sub
  if (!hasUsableAccessToken() || typeof subject !== 'string') {
    return
  }

  recordOfflineAuthorization(
    subject,
    extractPoultryFlowRoles(keycloak.tokenParsed),
  )
  sessionExpired = false
}

function clearAdapterTokenSilently(): void {
  suppressAdapterLogout = true
  try {
    keycloak.clearToken()
  } finally {
    suppressAdapterLogout = false
  }
}

function refreshAccessToken(): Promise<boolean> {
  refreshInFlight ??= keycloak
    .updateToken(30)
    .then((refreshed) => {
      if (refreshed && hasUsableAccessToken()) {
        synchronizeOfflineAuthorization()
        authenticationEvents?.onAuthenticated()
      }
      return refreshed
    })
    .finally(() => {
      refreshInFlight = undefined
    })

  return refreshInFlight
}

function expireOnlineSession(): void {
  if (sessionExpired) {
    return
  }

  sessionExpired = true
  clearAdapterTokenSilently()
  authenticationEvents?.onSessionExpired()
}

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
    return hasUsableAccessToken()
  },

  username(): string | undefined {
    const username = keycloak.tokenParsed?.preferred_username
    return typeof username === 'string' ? username : undefined
  },

  roles() {
    return extractPoultryFlowRoles(keycloak.tokenParsed)
  },

  synchronizeAuthorizationSnapshot(): void {
    synchronizeOfflineAuthorization()
  },

  login(): Promise<void> {
    clearOfflineAuthorization()
    clearAdapterTokenSilently()
    sessionExpired = false
    return keycloak.login({ redirectUri: applicationRoot })
  },

  async recoverCredentials(): Promise<void> {
    clearOfflineAuthorization()
    clearAdapterTokenSilently()
    sessionExpired = false
    const loginUrl = await keycloak.createLoginUrl({
      redirectUri: applicationRoot,
    })
    window.location.assign(credentialRecoveryUrl(loginUrl))
  },

  async logout(): Promise<void> {
    clearOfflineAuthorization()
    sessionExpired = false
    try {
      await keycloak.logout({ redirectUri: applicationRoot })
    } catch (error) {
      clearAdapterTokenSilently()
      throw error
    }
  },

  async refreshToken(): Promise<boolean> {
    const refreshed = await refreshAccessToken()
    if (!hasUsableAccessToken()) {
      throw new Error('Session expired')
    }
    return refreshed
  },

  async accessTokenForProtectedRequest(): Promise<string | undefined> {
    if (keycloak.authenticated !== true || typeof keycloak.token !== 'string') {
      expireOnlineSession()
      return undefined
    }

    try {
      await refreshAccessToken()
    } catch {
      if (hasUsableAccessToken()) {
        return keycloak.token
      }

      expireOnlineSession()
      return undefined
    }

    if (!hasUsableAccessToken()) {
      expireOnlineSession()
      return undefined
    }

    return keycloak.token
  },

  expireSession(): void {
    expireOnlineSession()
  },

  clearLocalSession(): void {
    sessionExpired = false
    clearOfflineAuthorization()
    clearAdapterTokenSilently()
  },

  setEvents(events: AuthenticationEvents): void {
    authenticationEvents = events
    keycloak.onAuthSuccess = () => {
      synchronizeOfflineAuthorization()
      events.onAuthenticated()
    }
    keycloak.onAuthError = events.onAuthenticationError
    keycloak.onAuthRefreshError = undefined
    keycloak.onAuthLogout = () => {
      if (suppressAdapterLogout || refreshInFlight) {
        return
      }

      sessionExpired = false
      clearOfflineAuthorization()
      events.onLoggedOut()
    }
    keycloak.onTokenExpired = events.onTokenExpired
  },
}
