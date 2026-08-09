import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  AuthenticationContext,
  type AuthenticationContextValue,
} from './AuthContext'
import { RequireRole } from './RequireRole'
import type { PoultryFlowRole } from './roles'

function authenticationWithRoles(
  roles: readonly PoultryFlowRole[],
): AuthenticationContextValue {
  return {
    status: 'authenticated',
    roles,
    hasRole: (role) => roles.includes(role),
    hasAnyRole: (requiredRoles) =>
      requiredRoles.some((role) => roles.includes(role)),
    login: vi.fn(),
    recoverCredentials: vi.fn(),
    logout: vi.fn(),
  }
}

function renderGuard(
  assignedRoles: readonly PoultryFlowRole[],
  requiredRoles: readonly PoultryFlowRole[],
) {
  render(
    <AuthenticationContext.Provider
      value={authenticationWithRoles(assignedRoles)}
    >
      <RequireRole anyOf={requiredRoles} fallback={<p>Access denied</p>}>
        <p>Protected content</p>
      </RequireRole>
    </AuthenticationContext.Provider>,
  )
}

describe('RequireRole', () => {
  it('renders protected content for an allowed role', () => {
    renderGuard(['MANAGER'], ['STAFF', 'MANAGER', 'OWNER'])

    expect(screen.getByText('Protected content')).toBeVisible()
    expect(screen.queryByText('Access denied')).not.toBeInTheDocument()
  })

  it('renders the denial fallback for a role outside the policy', () => {
    renderGuard(['ACCOUNTANT'], ['STAFF', 'MANAGER', 'OWNER'])

    expect(screen.getByText('Access denied')).toBeVisible()
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument()
  })

  it('keeps a viewer out of a write-oriented guard', () => {
    renderGuard(['VIEWER'], ['STAFF', 'MANAGER', 'OWNER'])

    expect(screen.getByText('Access denied')).toBeVisible()
  })
})
