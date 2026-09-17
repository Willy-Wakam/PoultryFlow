import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AuthenticationContext,
  type AuthenticationContextValue,
} from '../../auth/AuthContext'
import type { PoultryFlowRole } from '../../auth/roles'

const membershipApi = vi.hoisted(() => ({
  getCurrentFarmAccess: vi.fn(),
}))

vi.mock('./farmMembershipApi', () => ({
  getCurrentFarmAccess: membershipApi.getCurrentFarmAccess,
}))

import { RequireFarmRole } from './RequireFarmRole'

describe('RequireFarmRole', () => {
  beforeEach(() => vi.resetAllMocks())

  it('uses an active membership role without requiring the same token role', async () => {
    membershipApi.getCurrentFarmAccess.mockResolvedValue(access(['OWNER']))

    renderGuard([], 'authenticated')

    expect(await screen.findByText('Farm content')).toBeVisible()
    expect(screen.queryByText('Access denied')).not.toBeInTheDocument()
  })

  it('does not let a global token owner override farm membership roles', async () => {
    membershipApi.getCurrentFarmAccess.mockResolvedValue(access(['MANAGER']))

    renderGuard(['OWNER'], 'authenticated')

    expect(await screen.findByText('Access denied')).toBeVisible()
    expect(screen.queryByText('Farm content')).not.toBeInTheDocument()
  })

  it('denies a disabled membership response', async () => {
    membershipApi.getCurrentFarmAccess.mockResolvedValue({
      ...access(['OWNER']),
      status: 'DISABLED',
    })

    renderGuard(['OWNER'], 'authenticated')

    expect(await screen.findByText('Access denied')).toBeVisible()
  })

  it('does not query farm access without an authenticated online session', () => {
    renderGuard(['OWNER'], 'expired')

    expect(screen.getByText('Access denied')).toBeVisible()
    expect(membershipApi.getCurrentFarmAccess).not.toHaveBeenCalled()
  })
})

function renderGuard(
  tokenRoles: readonly PoultryFlowRole[],
  status: AuthenticationContextValue['status'],
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const authentication: AuthenticationContextValue = {
    status,
    subject: status === 'authenticated' ? 'test-subject' : undefined,
    roles: tokenRoles,
    hasRole: (role) => tokenRoles.includes(role),
    hasAnyRole: (roles) => roles.some((role) => tokenRoles.includes(role)),
    login: vi.fn(),
    recoverCredentials: vi.fn(),
    logout: vi.fn(),
  }
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>
      <AuthenticationContext.Provider value={authentication}>
        {children}
      </AuthenticationContext.Provider>
    </QueryClientProvider>
  )

  render(
    <RequireFarmRole anyOf={['OWNER']} fallback={<p>Access denied</p>}>
      <p>Farm content</p>
    </RequireFarmRole>,
    { wrapper: Wrapper },
  )
}

function access(roles: PoultryFlowRole[]) {
  return {
    farmId: '5f2941da-e571-48eb-8511-a93779b7a1a8',
    membershipId: '817c6428-76b5-47db-a10c-1c1f24b2fd4f',
    roles,
    status: 'ACTIVE' as const,
    bootstrapAuthority: false,
  }
}
