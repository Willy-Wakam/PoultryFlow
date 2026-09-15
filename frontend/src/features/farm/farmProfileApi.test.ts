import { beforeEach, describe, expect, it, vi } from 'vitest'

const protectedApiRequest = vi.hoisted(() => vi.fn())
vi.mock('../../auth/protectedApi', () => ({ protectedApiRequest }))

import type { FarmProfileRequest } from './farmProfile'
import {
  FarmProfileApiError,
  FarmProfileNotConfiguredError,
  getFarmProfile,
  saveFarmProfile,
} from './farmProfileApi'

const request: FarmProfileRequest = {
  name: 'Ferme Mvog-Betsi',
  contactEmail: null,
  contactPhone: null,
  timezone: 'Africa/Douala',
  countryCode: 'CM',
  currencyCode: 'XAF',
}

describe('farm profile API', () => {
  beforeEach(() => vi.resetAllMocks())

  it('treats the not-configured response as first-time setup', async () => {
    protectedApiRequest.mockResolvedValue(
      Response.json({ code: 'FARM_PROFILE_NOT_CONFIGURED' }, { status: 404 }),
    )

    await expect(getFarmProfile()).rejects.toBeInstanceOf(
      FarmProfileNotConfiguredError,
    )
  })

  it('sends one PUT through the protected API boundary', async () => {
    protectedApiRequest.mockResolvedValue(Response.json({ id: 'farm-id' }))

    await saveFarmProfile(request)

    expect(protectedApiRequest).toHaveBeenCalledOnce()
    expect(protectedApiRequest).toHaveBeenCalledWith(
      '/api/v1/farms/current',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(request),
      }),
    )
  })

  it('keeps only safe backend validation fields', async () => {
    protectedApiRequest.mockResolvedValue(
      Response.json(
        {
          code: 'VALIDATION_FAILED',
          violations: [
            { field: 'name', message: 'is required' },
            { field: 42, message: 'unsafe' },
          ],
        },
        { status: 400 },
      ),
    )

    const error = await saveFarmProfile(request).catch(
      (reason: unknown) => reason,
    )

    expect(error).toBeInstanceOf(FarmProfileApiError)
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      violations: [{ field: 'name', message: 'is required' }],
    })
  })
})
