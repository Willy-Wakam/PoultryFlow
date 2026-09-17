import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AuthenticationContext,
  type AuthenticationContextValue,
} from '../../auth/AuthContext'
import type { FarmMembership } from './farmMembership'

const membershipApi = vi.hoisted(() => ({
  inviteFarmMember: vi.fn(),
  listFarmMemberships: vi.fn(),
  updateFarmMembershipRoles: vi.fn(),
  updateFarmMembershipStatus: vi.fn(),
}))

vi.mock('./farmMembershipApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./farmMembershipApi')>()),
  ...membershipApi,
}))

import { UserAdministrationPanel } from './UserAdministrationPanel'
import { FarmMembershipApiError } from './farmMembershipApi'

const owner: FarmMembership = {
  id: '11111111-1111-4111-8111-111111111111',
  farmId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'owner@example.com',
  roles: ['OWNER'],
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-09-15T12:00:00Z',
  updatedAt: '2026-09-15T12:00:00Z',
}

const invited: FarmMembership = {
  ...owner,
  id: '22222222-2222-4222-8222-222222222222',
  email: 'invited@example.com',
  roles: ['STAFF'],
  status: 'INVITED',
}

const disabled: FarmMembership = {
  ...owner,
  id: '33333333-3333-4333-8333-333333333333',
  email: 'disabled@example.com',
  roles: ['VIEWER'],
  status: 'DISABLED',
}

describe('UserAdministrationPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    membershipApi.listFarmMemberships.mockResolvedValue([
      owner,
      invited,
      disabled,
    ])
  })

  it('shows invited, active, and disabled farm users', async () => {
    renderPanel()

    expect(await screen.findByText('owner@example.com')).toBeVisible()
    expect(screen.getByText('invited@example.com')).toBeVisible()
    expect(screen.getByText('disabled@example.com')).toBeVisible()
    expect(screen.getByText('active')).toBeVisible()
    expect(screen.getByText('invited')).toBeVisible()
    expect(screen.getByText('disabled')).toBeVisible()
  })

  it('invites an email with selected roles exactly once', async () => {
    membershipApi.inviteFarmMember.mockResolvedValue(invited)
    renderPanel()
    await screen.findByText('owner@example.com')

    fireEvent.change(screen.getByLabelText('Email address'), {
      target: { value: 'new@example.com' },
    })
    const initialRoles = screen.getByRole('group', { name: 'Initial roles' })
    fireEvent.click(within(initialRoles).getByLabelText('Viewer'))
    fireEvent.click(screen.getByRole('button', { name: 'Invite user' }))

    await waitFor(() =>
      expect(membershipApi.inviteFarmMember).toHaveBeenCalledWith(
        'new@example.com',
        ['STAFF', 'VIEWER'],
      ),
    )
    expect(membershipApi.inviteFarmMember).toHaveBeenCalledOnce()
    expect(await screen.findByText('Invitation recorded.')).toBeVisible()
  })

  it('updates membership roles without an automatic retry', async () => {
    membershipApi.updateFarmMembershipRoles.mockResolvedValue({
      ...owner,
      roles: ['OWNER', 'MANAGER'],
      version: 1,
    })
    renderPanel()
    const ownerRow = (await screen.findByText('owner@example.com')).closest(
      'article',
    )
    expect(ownerRow).not.toBeNull()

    const roleGroup = within(ownerRow!).getByRole('group', {
      name: 'Roles for owner@example.com',
    })
    fireEvent.click(within(roleGroup).getByLabelText('Manager'))
    fireEvent.click(
      within(ownerRow!).getByRole('button', { name: 'Save roles' }),
    )

    await waitFor(() =>
      expect(membershipApi.updateFarmMembershipRoles).toHaveBeenCalledWith(
        owner.id,
        ['OWNER', 'MANAGER'],
      ),
    )
    expect(membershipApi.updateFarmMembershipRoles).toHaveBeenCalledOnce()
    expect(await within(ownerRow!).findByText('Roles saved.')).toBeVisible()
  })

  it('soft-disables and re-enables memberships', async () => {
    membershipApi.updateFarmMembershipStatus
      .mockResolvedValueOnce({ ...invited, status: 'DISABLED' })
      .mockResolvedValueOnce({ ...disabled, status: 'ACTIVE' })
    renderPanel()
    const invitedRow = (await screen.findByText('invited@example.com')).closest(
      'article',
    )
    const disabledRow = screen
      .getByText('disabled@example.com')
      .closest('article')

    fireEvent.click(
      within(invitedRow!).getByRole('button', { name: 'Disable' }),
    )
    fireEvent.click(
      within(disabledRow!).getByRole('button', { name: 'Re-enable' }),
    )

    await waitFor(() => {
      expect(membershipApi.updateFarmMembershipStatus).toHaveBeenCalledWith(
        invited.id,
        false,
      )
      expect(membershipApi.updateFarmMembershipStatus).toHaveBeenCalledWith(
        disabled.id,
        true,
      )
    })
  })

  it('surfaces last-owner conflicts without exposing server detail', async () => {
    membershipApi.updateFarmMembershipStatus.mockRejectedValue(
      new FarmMembershipApiError(409, 'LAST_ACTIVE_OWNER_REQUIRED'),
    )
    renderPanel()
    const ownerRow = (await screen.findByText('owner@example.com')).closest(
      'article',
    )

    fireEvent.click(within(ownerRow!).getByRole('button', { name: 'Disable' }))

    expect(
      await within(ownerRow!).findByText(
        'At least one active farm owner must remain.',
      ),
    ).toBeVisible()
  })
})

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const authentication: AuthenticationContextValue = {
    status: 'authenticated',
    subject: 'owner-subject',
    roles: [],
    hasRole: () => false,
    hasAnyRole: () => false,
    login: vi.fn(),
    recoverCredentials: vi.fn(),
    logout: vi.fn(),
  }

  render(
    <QueryClientProvider client={queryClient}>
      <AuthenticationContext.Provider value={authentication}>
        <UserAdministrationPanel />
      </AuthenticationContext.Provider>
    </QueryClientProvider>,
  )
}
