import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FarmLocation } from './farmLocation'

const locationApi = vi.hoisted(() => ({
  createFarmLocation: vi.fn(),
  listFarmLocations: vi.fn(),
  updateFarmLocation: vi.fn(),
  updateFarmLocationStatus: vi.fn(),
}))

vi.mock('./farmLocationApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./farmLocationApi')>()),
  ...locationApi,
}))

import { FarmLocationAdministrationPanel } from './FarmLocationAdministrationPanel'
import { FarmLocationApiError } from './farmLocationApi'

const active: FarmLocation = {
  id: '11111111-1111-4111-8111-111111111111',
  farmId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  name: 'House One',
  type: 'HOUSE',
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-09-17T12:00:00Z',
  updatedAt: '2026-09-17T12:00:00Z',
}

const inactive: FarmLocation = {
  ...active,
  id: '22222222-2222-4222-8222-222222222222',
  name: 'Feed Store',
  type: 'STORAGE',
  status: 'INACTIVE',
  version: 2,
}

describe('FarmLocationAdministrationPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    locationApi.listFarmLocations.mockResolvedValue([active, inactive])
  })

  it('shows active and inactive locations with a clear inactive state', async () => {
    renderPanel()

    expect(await screen.findByDisplayValue('House One')).toBeVisible()
    expect(screen.getByDisplayValue('Feed Store')).toBeVisible()
    expect(screen.getByText('active')).toBeVisible()
    expect(screen.getByText('inactive')).toBeVisible()
    expect(locationApi.listFarmLocations).toHaveBeenCalledWith(true)
  })

  it('creates a selected location type without retrying', async () => {
    locationApi.createFarmLocation.mockResolvedValue({
      ...active,
      id: '33333333-3333-4333-8333-333333333333',
      name: 'Grower Pen',
      type: 'PEN',
    })
    renderPanel()
    await screen.findByDisplayValue('House One')

    fireEvent.change(screen.getByLabelText('Location name'), {
      target: { value: 'Grower Pen' },
    })
    fireEvent.change(document.querySelector('#location-type')!, {
      target: { value: 'PEN' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create location' }))

    await waitFor(() =>
      expect(locationApi.createFarmLocation).toHaveBeenCalledWith({
        name: 'Grower Pen',
        type: 'PEN',
      }),
    )
    expect(locationApi.createFarmLocation).toHaveBeenCalledOnce()
    expect(await screen.findByText('Location created.')).toBeVisible()
  })

  it('edits a location and sends its current optimistic version', async () => {
    locationApi.updateFarmLocation.mockResolvedValue({
      ...active,
      name: 'Brooder House',
      type: 'OTHER',
      version: 1,
    })
    renderPanel()
    const activeRow = (await screen.findByDisplayValue('House One')).closest(
      'article',
    )

    fireEvent.change(within(activeRow!).getByLabelText('Name'), {
      target: { value: 'Brooder House' },
    })
    fireEvent.change(within(activeRow!).getByLabelText('Type'), {
      target: { value: 'OTHER' },
    })
    fireEvent.click(
      within(activeRow!).getByRole('button', { name: 'Save changes' }),
    )

    await waitFor(() =>
      expect(locationApi.updateFarmLocation).toHaveBeenCalledWith(active, {
        name: 'Brooder House',
        type: 'OTHER',
      }),
    )
    expect(locationApi.updateFarmLocation).toHaveBeenCalledOnce()
    expect(await within(activeRow!).findByText('Location saved.')).toBeVisible()
  })

  it('deactivates and reactivates locations without automatic retries', async () => {
    locationApi.updateFarmLocationStatus
      .mockResolvedValueOnce({ ...active, status: 'INACTIVE', version: 1 })
      .mockResolvedValueOnce({ ...inactive, status: 'ACTIVE', version: 3 })
    renderPanel()
    const activeRow = (await screen.findByDisplayValue('House One')).closest(
      'article',
    )
    const inactiveRow = screen
      .getByDisplayValue('Feed Store')
      .closest('article')

    fireEvent.click(
      within(activeRow!).getByRole('button', { name: 'Deactivate' }),
    )
    fireEvent.click(
      within(inactiveRow!).getByRole('button', { name: 'Reactivate' }),
    )

    await waitFor(() => {
      expect(locationApi.updateFarmLocationStatus).toHaveBeenCalledWith(
        active,
        'INACTIVE',
      )
      expect(locationApi.updateFarmLocationStatus).toHaveBeenCalledWith(
        inactive,
        'ACTIVE',
      )
    })
    expect(locationApi.updateFarmLocationStatus).toHaveBeenCalledTimes(2)
  })

  it('surfaces safe duplicate and optimistic conflict messages', async () => {
    locationApi.updateFarmLocation
      .mockRejectedValueOnce(
        new FarmLocationApiError(409, 'FARM_LOCATION_NAME_ALREADY_EXISTS'),
      )
      .mockRejectedValueOnce(
        new FarmLocationApiError(409, 'CONCURRENT_MODIFICATION'),
      )
    renderPanel()
    const activeRow = (await screen.findByDisplayValue('House One')).closest(
      'article',
    )

    fireEvent.click(
      within(activeRow!).getByRole('button', { name: 'Save changes' }),
    )
    expect(
      await within(activeRow!).findByText(
        'That location name is already in use.',
      ),
    ).toBeVisible()

    fireEvent.click(
      within(activeRow!).getByRole('button', { name: 'Save changes' }),
    )
    expect(
      await within(activeRow!).findByText(
        'This location changed. Refresh and try again.',
      ),
    ).toBeVisible()
  })
})

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  render(
    <QueryClientProvider client={queryClient}>
      <FarmLocationAdministrationPanel />
    </QueryClientProvider>,
  )
}
