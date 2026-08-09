import { expect, it, vi } from 'vitest'

const init = vi.hoisted(() => vi.fn().mockResolvedValue(false))

vi.mock('keycloak-js', () => ({
  default: class KeycloakMock {
    init = init
  },
}))

import { keycloakClient } from './keycloak'

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
