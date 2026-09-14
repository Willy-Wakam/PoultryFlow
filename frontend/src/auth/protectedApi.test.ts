import { beforeEach, describe, expect, it, vi } from 'vitest'

const keycloakClient = vi.hoisted(() => ({
  accessTokenForProtectedRequest: vi.fn(),
  expireSession: vi.fn(),
}))

vi.mock('./keycloak', () => ({ keycloakClient }))

import {
  InvalidProtectedApiDestinationError,
  protectedApiRequest,
  SessionExpiredError,
} from './protectedApi'

const fetchMock = vi.fn()

describe('protected API requests', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    keycloakClient.accessTokenForProtectedRequest.mockResolvedValue(
      'access-token',
    )
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
  })

  it('obtains a current token before attaching Bearer credentials', async () => {
    await protectedApiRequest('/api/flocks?active=true', {
      headers: { Accept: 'application/json' },
    })

    expect(keycloakClient.accessTokenForProtectedRequest).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledOnce()
    const [destination, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(destination).toBe('/api/flocks?active=true')
    expect(new Headers(init.headers).get('Authorization')).toBe(
      'Bearer access-token',
    )
  })

  it('blocks a request when no valid access token is available', async () => {
    keycloakClient.accessTokenForProtectedRequest.mockResolvedValue(undefined)

    await expect(protectedApiRequest('/api/flocks')).rejects.toBeInstanceOf(
      SessionExpiredError,
    )
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('expires on one backend 401 without retrying a write', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))

    await expect(
      protectedApiRequest('/api/flocks', {
        method: 'POST',
        body: JSON.stringify({ name: 'Layer flock' }),
      }),
    ).rejects.toBeInstanceOf(SessionExpiredError)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(keycloakClient.expireSession).toHaveBeenCalledOnce()
  })

  it('returns a backend 403 without expiring the session', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))

    const response = await protectedApiRequest('/api/admin')

    expect(response.status).toBe(403)
    expect(keycloakClient.expireSession).not.toHaveBeenCalled()
  })

  it.each([
    'https://example.com/api/flocks',
    '/actuator/health',
    'https://user:password@localhost/api/flocks',
  ])(
    'rejects a non-protected destination before obtaining a token: %s',
    async (destination) => {
      await expect(protectedApiRequest(destination)).rejects.toBeInstanceOf(
        InvalidProtectedApiDestinationError,
      )
      expect(
        keycloakClient.accessTokenForProtectedRequest,
      ).not.toHaveBeenCalled()
      expect(fetchMock).not.toHaveBeenCalled()
    },
  )
})
