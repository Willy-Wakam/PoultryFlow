import { keycloakClient } from './keycloak'

export class SessionExpiredError extends Error {
  readonly code = 'SESSION_EXPIRED'

  constructor() {
    super('A valid online session is required.')
    this.name = 'SessionExpiredError'
  }
}

export class InvalidProtectedApiDestinationError extends Error {
  readonly code = 'INVALID_PROTECTED_API_DESTINATION'

  constructor() {
    super('Protected API requests must use a same-origin API path.')
    this.name = 'InvalidProtectedApiDestinationError'
  }
}

function resolveProtectedApiUrl(destination: string | URL): URL {
  let url: URL

  try {
    url = new URL(destination.toString(), window.location.origin)
  } catch {
    throw new InvalidProtectedApiDestinationError()
  }

  if (
    url.origin !== window.location.origin ||
    !url.pathname.startsWith('/api/') ||
    url.username !== '' ||
    url.password !== ''
  ) {
    throw new InvalidProtectedApiDestinationError()
  }

  return url
}

export async function protectedApiRequest(
  destination: string | URL,
  init?: RequestInit,
): Promise<Response> {
  const url = resolveProtectedApiUrl(destination)
  const accessToken = await keycloakClient.accessTokenForProtectedRequest()

  if (!accessToken) {
    throw new SessionExpiredError()
  }

  const headers = new Headers(init?.headers)
  headers.set('Authorization', `Bearer ${accessToken}`)

  const response = await fetch(`${url.pathname}${url.search}`, {
    ...init,
    headers,
  })

  if (response.status === 401) {
    keycloakClient.expireSession()
    throw new SessionExpiredError()
  }

  return response
}
