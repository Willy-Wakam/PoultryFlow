import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthenticationPanel } from './AuthenticationPanel'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const keycloakClient = vi.hoisted(() => ({
  initialize: vi.fn(),
  isAuthenticated: vi.fn(),
  username: vi.fn(),
  roles: vi.fn(),
  login: vi.fn(),
  recoverCredentials: vi.fn(),
  logout: vi.fn(),
  refreshToken: vi.fn(),
  clearToken: vi.fn(),
  setEvents: vi.fn(),
}))

vi.mock('./keycloak', () => ({ keycloakClient }))

function AuthenticationUnderTest({ children }: PropsWithChildren) {
  return <AuthProvider>{children}</AuthProvider>
}

function RoleStateProbe() {
  const authentication = useAuth()
  return (
    <p>
      {authentication.status}:{authentication.roles.join(',')}:
      {authentication.hasRole('STAFF') ? 'staff' : 'not-staff'}
    </p>
  )
}

describe('PoultryFlow authentication', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    keycloakClient.initialize.mockResolvedValue(false)
    keycloakClient.isAuthenticated.mockReturnValue(false)
    keycloakClient.roles.mockReturnValue([])
    keycloakClient.login.mockResolvedValue(undefined)
    keycloakClient.recoverCredentials.mockResolvedValue(undefined)
    keycloakClient.logout.mockResolvedValue(undefined)
    keycloakClient.refreshToken.mockResolvedValue(false)
  })

  it('shows Sign in after unauthenticated initialization', async () => {
    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    expect(
      await screen.findByRole('button', { name: 'Sign in' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Forgot password?' }),
    ).toBeInTheDocument()
  })

  it('delegates Sign in to Keycloak', async () => {
    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    fireEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(keycloakClient.login).toHaveBeenCalledOnce()
  })

  it('delegates credential recovery to Keycloak', async () => {
    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    fireEvent.click(
      await screen.findByRole('button', { name: 'Forgot password?' }),
    )

    expect(keycloakClient.recoverCredentials).toHaveBeenCalledOnce()
  })

  it('shows authenticated identity and delegates Sign out to Keycloak', async () => {
    keycloakClient.initialize.mockResolvedValue(true)
    keycloakClient.isAuthenticated.mockReturnValue(true)
    keycloakClient.username.mockReturnValue('local-farmer')

    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    expect(await screen.findByText('Signed in as local-farmer.')).toBeVisible()

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(keycloakClient.logout).toHaveBeenCalledOnce()
  })

  it('shows a safe generic message when initialization fails', async () => {
    keycloakClient.initialize.mockRejectedValue(
      new Error('sensitive identity-provider detail'),
    )

    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Authentication could not be completed. Please try again.',
    )
    expect(
      screen.queryByText('sensitive identity-provider detail'),
    ).not.toBeInTheDocument()
  })

  it('shows only a safe generic message when recovery fails', async () => {
    keycloakClient.recoverCredentials.mockRejectedValue(
      new Error('sensitive identity-provider recovery detail'),
    )

    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    fireEvent.click(
      await screen.findByRole('button', { name: 'Forgot password?' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Authentication could not be completed. Please try again.',
    )
    expect(
      screen.queryByText('sensitive identity-provider recovery detail'),
    ).not.toBeInTheDocument()
  })

  it('updates typed roles after Keycloak reports authentication', async () => {
    let onAuthenticated: (() => void) | undefined
    keycloakClient.setEvents.mockImplementation((events) => {
      onAuthenticated = events.onAuthenticated
    })

    render(<RoleStateProbe />, { wrapper: AuthenticationUnderTest })

    expect(await screen.findByText('unauthenticated::not-staff')).toBeVisible()

    keycloakClient.isAuthenticated.mockReturnValue(true)
    keycloakClient.roles.mockReturnValue(['STAFF'])
    await act(async () => onAuthenticated?.())

    expect(await screen.findByText('authenticated:STAFF:staff')).toBeVisible()
  })

  it('clears authentication when token refresh fails', async () => {
    keycloakClient.initialize.mockResolvedValue(true)
    keycloakClient.isAuthenticated.mockReturnValue(true)
    keycloakClient.setEvents.mockImplementation((events) => {
      keycloakClient.refreshToken.mockRejectedValueOnce(
        new Error('sensitive refresh detail'),
      )
      queueMicrotask(events.onTokenExpired)
    })

    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    await waitFor(() => expect(keycloakClient.clearToken).toHaveBeenCalled())
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Authentication could not be completed. Please try again.',
    )
  })
})
