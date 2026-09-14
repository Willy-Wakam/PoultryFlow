import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearOfflineAuthorization,
  getOfflineAuthorizationSnapshot,
  recordOfflineAuthorization,
} from './offlineAuthorization'

type KeycloakMockInstance = {
  authenticated?: boolean
  token?: string
  tokenParsed?: Record<string, unknown>
  timeSkew?: number
  onAuthSuccess?: () => void
  onAuthError?: () => void
  onAuthRefreshError?: () => void
  onAuthLogout?: () => void
  onTokenExpired?: () => void
}

const adapter = vi.hoisted(() => ({
  instance: undefined as KeycloakMockInstance | undefined,
  init: vi.fn().mockResolvedValue(false),
  login: vi.fn().mockResolvedValue(undefined),
  logout: vi.fn().mockResolvedValue(undefined),
  createLoginUrl: vi.fn(),
  updateToken: vi.fn().mockResolvedValue(false),
  clearToken: vi.fn(),
}))

vi.mock('keycloak-js', () => ({
  default: class KeycloakMock implements KeycloakMockInstance {
    authenticated = false
    token?: string
    tokenParsed?: Record<string, unknown>
    timeSkew = 0
    onAuthSuccess?: () => void
    onAuthError?: () => void
    onAuthRefreshError?: () => void
    onAuthLogout?: () => void
    onTokenExpired?: () => void

    constructor() {
      adapter.instance = this
    }

    init = adapter.init
    login = adapter.login
    logout = adapter.logout
    createLoginUrl = adapter.createLoginUrl
    updateToken = adapter.updateToken

    clearToken = () => {
      adapter.clearToken()
      this.authenticated = false
      this.token = undefined
      this.tokenParsed = undefined
      this.onAuthLogout?.()
    }
  },
}))

import { credentialRecoveryUrl, keycloakClient } from './keycloak'

const noOpEvents = {
  onAuthenticated: vi.fn(),
  onAuthenticationError: vi.fn(),
  onLoggedOut: vi.fn(),
  onSessionExpired: vi.fn(),
  onTokenExpired: vi.fn(),
}

function authenticate(
  roles: readonly string[] = ['STAFF'],
  expiresAt = Math.floor(Date.now() / 1000) + 120,
  token = 'current-access-token',
) {
  const instance = adapter.instance
  if (!instance) {
    throw new Error('Keycloak mock was not constructed')
  }

  instance.authenticated = true
  instance.token = token
  instance.tokenParsed = {
    sub: 'user-123',
    preferred_username: 'local-farmer',
    exp: expiresAt,
    resource_access: {
      'poultryflow-api': { roles },
    },
  }
  instance.timeSkew = 0
  keycloakClient.synchronizeAuthorizationSnapshot()
}

describe('Keycloak client', () => {
  beforeEach(() => {
    keycloakClient.setEvents(noOpEvents)
    keycloakClient.clearLocalSession()
    clearOfflineAuthorization()
    vi.clearAllMocks()
    adapter.init.mockResolvedValue(false)
    adapter.login.mockResolvedValue(undefined)
    adapter.logout.mockResolvedValue(undefined)
    adapter.updateToken.mockResolvedValue(false)
  })

  it('initializes one Keycloak instance only once for repeated consumers', async () => {
    const firstInitialization = keycloakClient.initialize()
    const repeatedInitialization = keycloakClient.initialize()

    expect(repeatedInitialization).toBe(firstInitialization)
    await expect(firstInitialization).resolves.toBe(false)
    expect(adapter.init).toHaveBeenCalledOnce()
    expect(adapter.init).toHaveBeenCalledWith(
      expect.objectContaining({
        flow: 'standard',
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        useNonce: true,
      }),
    )
  })

  it('changes only the final login path segment for credential recovery', () => {
    const loginUrl =
      'https://identity.example/auth/realms/poultryflow/protocol/openid-connect/auth' +
      '?client_id=poultryflow-web' +
      '&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F' +
      '&state=generated-state' +
      '&nonce=generated-nonce' +
      '&code_challenge=generated-challenge' +
      '&code_challenge_method=S256'

    const recoveryUrl = new URL(credentialRecoveryUrl(loginUrl))
    const generatedLoginUrl = new URL(loginUrl)

    expect(recoveryUrl.pathname).toBe(
      '/auth/realms/poultryflow/protocol/openid-connect/forgot-credentials',
    )
    expect(recoveryUrl.search).toBe(generatedLoginUrl.search)
  })

  it.each([
    'not a URL',
    'https://identity.example/realms/poultryflow/account',
    'file:///realms/poultryflow/protocol/openid-connect/auth',
  ])('rejects an unexpected generated login URL safely: %s', (loginUrl) => {
    expect(() => credentialRecoveryUrl(loginUrl)).toThrow(
      'Unexpected identity service login URL',
    )
  })

  it('uses a still-valid current token when a proactive refresh fails', async () => {
    authenticate()
    adapter.updateToken.mockRejectedValueOnce(new Error('identity unavailable'))

    await expect(keycloakClient.accessTokenForProtectedRequest()).resolves.toBe(
      'current-access-token',
    )
    expect(adapter.updateToken).toHaveBeenCalledWith(30)
    expect(adapter.clearToken).not.toHaveBeenCalled()
  })

  it('expires a session instead of returning an expired token', async () => {
    const onSessionExpired = vi.fn()
    keycloakClient.setEvents({ ...noOpEvents, onSessionExpired })
    authenticate(['MANAGER'])
    if (adapter.instance?.tokenParsed) {
      adapter.instance.tokenParsed.exp = Math.floor(Date.now() / 1000) - 1
    }
    adapter.updateToken.mockRejectedValueOnce(new Error('refresh rejected'))

    await expect(
      keycloakClient.accessTokenForProtectedRequest(),
    ).resolves.toBeUndefined()
    expect(onSessionExpired).toHaveBeenCalledOnce()
    expect(adapter.clearToken).toHaveBeenCalledOnce()
    expect(getOfflineAuthorizationSnapshot()).toEqual({
      subject: 'user-123',
      roles: ['MANAGER'],
    })
  })

  it('shares one refresh across concurrent protected requests', async () => {
    authenticate()
    let resolveRefresh: ((refreshed: boolean) => void) | undefined
    adapter.updateToken.mockReturnValueOnce(
      new Promise<boolean>((resolve) => {
        resolveRefresh = resolve
      }),
    )

    const firstToken = keycloakClient.accessTokenForProtectedRequest()
    const secondToken = keycloakClient.accessTokenForProtectedRequest()

    expect(adapter.updateToken).toHaveBeenCalledOnce()
    resolveRefresh?.(false)
    await expect(firstToken).resolves.toBe('current-access-token')
    await expect(secondToken).resolves.toBe('current-access-token')
  })

  it('updates the offline authorization snapshot after token refresh', async () => {
    const onAuthenticated = vi.fn()
    keycloakClient.setEvents({ ...noOpEvents, onAuthenticated })
    authenticate(['STAFF'])
    adapter.updateToken.mockImplementationOnce(async () => {
      authenticate(['OWNER'], Math.floor(Date.now() / 1000) + 120, 'new-token')
      return true
    })

    await expect(keycloakClient.accessTokenForProtectedRequest()).resolves.toBe(
      'new-token',
    )
    expect(getOfflineAuthorizationSnapshot()).toEqual({
      subject: 'user-123',
      roles: ['OWNER'],
    })
    expect(onAuthenticated).toHaveBeenCalledOnce()
  })

  it('clears the offline snapshot for a new sign-in attempt', async () => {
    recordOfflineAuthorization('previous-user', ['VIEWER'])

    await keycloakClient.login()

    expect(getOfflineAuthorizationSnapshot()).toBeUndefined()
    expect(adapter.clearToken).toHaveBeenCalledOnce()
    expect(adapter.login).toHaveBeenCalledOnce()
  })

  it('keeps local logout complete when remote logout fails', async () => {
    authenticate(['ACCOUNTANT'])
    adapter.logout.mockRejectedValueOnce(new Error('identity unavailable'))

    await expect(keycloakClient.logout()).rejects.toThrow(
      'identity unavailable',
    )
    expect(getOfflineAuthorizationSnapshot()).toBeUndefined()
    expect(adapter.clearToken).toHaveBeenCalledOnce()
  })
})
