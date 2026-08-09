import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthenticationPanel } from './AuthenticationPanel'
import { AuthProvider } from './AuthProvider'

const keycloakClient = vi.hoisted(() => ({
  initialize: vi.fn(),
  isAuthenticated: vi.fn(),
  username: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  refreshToken: vi.fn(),
  clearToken: vi.fn(),
  setEvents: vi.fn(),
}))

vi.mock('./keycloak', () => ({ keycloakClient }))

function AuthenticationUnderTest({ children }: PropsWithChildren) {
  return <AuthProvider>{children}</AuthProvider>
}

describe('PoultryFlow authentication', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    keycloakClient.initialize.mockResolvedValue(false)
    keycloakClient.isAuthenticated.mockReturnValue(false)
    keycloakClient.login.mockResolvedValue(undefined)
    keycloakClient.logout.mockResolvedValue(undefined)
    keycloakClient.refreshToken.mockResolvedValue(false)
  })

  it('shows Sign in after unauthenticated initialization', async () => {
    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    expect(
      await screen.findByRole('button', { name: 'Sign in' }),
    ).toBeInTheDocument()
  })

  it('delegates Sign in to Keycloak', async () => {
    render(<AuthenticationPanel />, { wrapper: AuthenticationUnderTest })

    fireEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(keycloakClient.login).toHaveBeenCalledOnce()
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
      'Sign-in could not be completed. Please try again.',
    )
    expect(
      screen.queryByText('sensitive identity-provider detail'),
    ).not.toBeInTheDocument()
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
      'Sign-in could not be completed. Please try again.',
    )
  })
})
