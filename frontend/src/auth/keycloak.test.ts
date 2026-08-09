import { expect, it, vi } from 'vitest'

const init = vi.hoisted(() => vi.fn().mockResolvedValue(false))

vi.mock('keycloak-js', () => ({
  default: class KeycloakMock {
    init = init
  },
}))

import { credentialRecoveryUrl, keycloakClient } from './keycloak'

it('initializes one Keycloak instance only once for repeated consumers', async () => {
  const firstInitialization = keycloakClient.initialize()
  const repeatedInitialization = keycloakClient.initialize()

  expect(repeatedInitialization).toBe(firstInitialization)
  await expect(firstInitialization).resolves.toBe(false)
  expect(init).toHaveBeenCalledOnce()
  expect(init).toHaveBeenCalledWith(
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
