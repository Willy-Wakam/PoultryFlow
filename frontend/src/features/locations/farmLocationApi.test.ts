import { beforeEach, describe, expect, it, vi } from 'vitest'

const protectedApiRequest = vi.hoisted(() => vi.fn())
vi.mock('../../auth/protectedApi', () => ({ protectedApiRequest }))

import type { FarmLocation } from './farmLocation'
import {
  createFarmLocation,
  FarmLocationApiError,
  listFarmLocations,
  updateFarmLocation,
  updateFarmLocationStatus,
} from './farmLocationApi'

const location: FarmLocation = {
  id: '11111111-1111-4111-8111-111111111111',
  farmId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  name: 'House One',
  type: 'HOUSE',
  status: 'ACTIVE',
  version: 3,
  createdAt: '2026-09-17T12:00:00Z',
  updatedAt: '2026-09-17T12:00:00Z',
}

describe('farm location API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    protectedApiRequest.mockImplementation(() =>
      Promise.resolve(Response.json(location)),
    )
  })

  it('requests inactive locations only for the administration view', async () => {
    protectedApiRequest.mockResolvedValue(Response.json([location]))

    await listFarmLocations(true)

    expect(protectedApiRequest).toHaveBeenCalledWith(
      '/api/v1/farms/current/locations?includeInactive=true',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
  })

  it('trims names and sends each write exactly once with the current version', async () => {
    await createFarmLocation({ name: '  House Two  ', type: 'HOUSE' })
    await updateFarmLocation(location, { name: '  Main Pen  ', type: 'PEN' })
    await updateFarmLocationStatus(location, 'INACTIVE')

    expect(protectedApiRequest).toHaveBeenCalledTimes(3)
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      1,
      '/api/v1/farms/current/locations',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ name: 'House Two', type: 'HOUSE' }),
      }),
    )
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      2,
      `/api/v1/farms/current/locations/${location.id}`,
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ name: 'Main Pen', type: 'PEN', version: 3 }),
      }),
    )
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      3,
      `/api/v1/farms/current/locations/${location.id}/status`,
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ status: 'INACTIVE', version: 3 }),
      }),
    )
  })

  it('maps only the safe problem code from a backend conflict', async () => {
    protectedApiRequest.mockResolvedValue(
      Response.json(
        {
          code: 'FARM_LOCATION_NAME_ALREADY_EXISTS',
          detail: 'internal detail',
        },
        { status: 409 },
      ),
    )

    const error = await createFarmLocation({
      name: 'House One',
      type: 'HOUSE',
    }).catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(FarmLocationApiError)
    expect(error).toMatchObject({
      status: 409,
      code: 'FARM_LOCATION_NAME_ALREADY_EXISTS',
      message: 'The farm location request could not be completed.',
    })
  })
})
