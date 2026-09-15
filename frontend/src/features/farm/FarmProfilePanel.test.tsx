import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AuthenticationContext,
  type AuthenticationContextValue,
} from '../../auth/AuthContext'
import { RequireRole } from '../../auth/RequireRole'
import type { PoultryFlowRole } from '../../auth/roles'
import type { FarmProfile } from './farmProfile'

const farmApi = vi.hoisted(() => ({
  getFarmProfile: vi.fn(),
  saveFarmProfile: vi.fn(),
}))
vi.mock('./farmProfileApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./farmProfileApi')>()),
  getFarmProfile: farmApi.getFarmProfile,
  saveFarmProfile: farmApi.saveFarmProfile,
}))

import { FarmProfilePanel } from './FarmProfilePanel'
import {
  FarmProfileApiError,
  FarmProfileNotConfiguredError,
} from './farmProfileApi'

const profile: FarmProfile = {
  id: '22e1fbfd-2243-40df-b823-52faf03d1507',
  name: 'Ferme Mvog-Betsi',
  contactEmail: 'owner@example.com',
  contactPhone: '+237 600 000 000',
  timezone: 'Africa/Douala',
  countryCode: 'CM',
  currencyCode: 'XAF',
  version: 0,
  createdAt: '2026-09-14T12:00:00Z',
  updatedAt: '2026-09-14T12:00:00Z',
}

describe('FarmProfilePanel', () => {
  beforeEach(() => vi.resetAllMocks())

  it('shows the editor to an authenticated owner', async () => {
    farmApi.getFarmProfile.mockResolvedValue(profile)
    renderEditor(['OWNER'])

    expect(await screen.findByLabelText('Farm name')).toHaveValue(
      'Ferme Mvog-Betsi',
    )
  })

  it.each([
    {
      roles: ['MANAGER'] as PoultryFlowRole[],
      status: 'authenticated' as const,
    },
    { roles: ['OWNER'] as PoultryFlowRole[], status: 'expired' as const },
  ])(
    'does not expose the editor to $status users with $roles',
    ({ roles, status }) => {
      renderEditor(roles, status)

      expect(screen.queryByText('Farm profile')).not.toBeInTheDocument()
      expect(farmApi.getFarmProfile).not.toHaveBeenCalled()
    },
  )

  it('initializes first-time setup without inventing a farm name', async () => {
    farmApi.getFarmProfile.mockRejectedValue(
      new FarmProfileNotConfiguredError(),
    )
    renderEditor(['OWNER'])

    expect(await screen.findByText('Setup required')).toBeVisible()
    expect(screen.getByLabelText('Farm name')).toHaveValue('')
    expect(screen.getByLabelText('Timezone')).toHaveValue('Africa/Douala')
    expect(screen.getByLabelText('Country code')).toHaveValue('CM')
    expect(screen.getByLabelText('Currency code')).toHaveValue('XAF')
  })

  it('populates every editable field from an existing profile', async () => {
    farmApi.getFarmProfile.mockResolvedValue(profile)
    renderEditor(['OWNER'])

    expect(await screen.findByLabelText('Contact email')).toHaveValue(
      'owner@example.com',
    )
    expect(screen.getByLabelText('Contact phone')).toHaveValue(
      '+237 600 000 000',
    )
    expect(screen.getByLabelText('Country code')).toHaveValue('CM')
  })

  it('blocks invalid values before calling the API', async () => {
    farmApi.getFarmProfile.mockRejectedValue(
      new FarmProfileNotConfiguredError(),
    )
    renderEditor(['OWNER'])
    await screen.findByText('Setup required')

    fireEvent.change(screen.getByLabelText('Farm name'), {
      target: { value: 'Test Farm' },
    })
    fireEvent.change(screen.getByLabelText('Timezone'), {
      target: { value: 'Mars/Olympus' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Enter a valid IANA timezone')).toBeVisible()
    expect(farmApi.saveFarmProfile).not.toHaveBeenCalled()
  })

  it('saves once and refreshes the rendered profile', async () => {
    farmApi.getFarmProfile.mockResolvedValue(profile)
    farmApi.saveFarmProfile.mockResolvedValue({
      ...profile,
      name: 'Updated Farm',
      version: 1,
    })
    renderEditor(['OWNER'])
    const name = await screen.findByLabelText('Farm name')

    fireEvent.change(name, { target: { value: 'Updated Farm' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Farm profile saved.')).toBeVisible()
    expect(farmApi.saveFarmProfile).toHaveBeenCalledOnce()
    expect(name).toHaveValue('Updated Farm')
  })

  it('preserves unsaved values after an ordinary network failure', async () => {
    farmApi.getFarmProfile.mockResolvedValue(profile)
    farmApi.saveFarmProfile.mockRejectedValue(new TypeError('Network failed'))
    renderEditor(['OWNER'])
    const name = await screen.findByLabelText('Farm name')

    fireEvent.change(name, { target: { value: 'Unsaved Farm Name' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(
      await screen.findByText(
        'Farm profile could not be saved. Your changes are still here.',
      ),
    ).toBeVisible()
    expect(name).toHaveValue('Unsaved Farm Name')
    expect(farmApi.saveFarmProfile).toHaveBeenCalledOnce()
  })

  it('maps safe backend validation to the matching field', async () => {
    farmApi.getFarmProfile.mockResolvedValue(profile)
    farmApi.saveFarmProfile.mockRejectedValue(
      new FarmProfileApiError(400, 'VALIDATION_FAILED', [
        { field: 'name', message: 'Farm name is already in use' },
      ]),
    )
    renderEditor(['OWNER'])
    await screen.findByLabelText('Farm name')

    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Farm name is already in use')).toBeVisible()
  })

  it('disables duplicate submission while a PUT is pending', async () => {
    farmApi.getFarmProfile.mockResolvedValue(profile)
    farmApi.saveFarmProfile.mockImplementation(
      () => new Promise(() => undefined),
    )
    renderEditor(['OWNER'])
    const save = await screen.findByRole('button', { name: 'Save profile' })

    fireEvent.click(save)
    await waitFor(() => expect(save).toBeDisabled())
    fireEvent.click(save)

    expect(farmApi.saveFarmProfile).toHaveBeenCalledOnce()
  })
})

function renderEditor(
  roles: readonly PoultryFlowRole[],
  status: AuthenticationContextValue['status'] = 'authenticated',
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const authentication: AuthenticationContextValue = {
    status,
    roles,
    hasRole: (role) => roles.includes(role),
    hasAnyRole: (required) => required.some((role) => roles.includes(role)),
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
    <RequireRole anyOf={['OWNER']}>
      <FarmProfilePanel />
    </RequireRole>,
    { wrapper: Wrapper },
  )
}
