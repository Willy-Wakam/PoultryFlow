import { beforeEach, describe, expect, it, vi } from 'vitest'

const protectedApiRequest = vi.hoisted(() => vi.fn())
vi.mock('../../auth/protectedApi', () => ({ protectedApiRequest }))

import {
  FarmMembershipApiError,
  getCurrentFarmAccess,
  inviteFarmMember,
  listFarmMemberships,
  updateFarmMembershipRoles,
  updateFarmMembershipStatus,
} from './farmMembershipApi'

describe('farm membership API', () => {
  beforeEach(() => vi.resetAllMocks())

  it('loads current access and membership lists through the protected boundary', async () => {
    protectedApiRequest
      .mockResolvedValueOnce(jsonResponse({ roles: ['OWNER'] }))
      .mockResolvedValueOnce(jsonResponse([]))

    await getCurrentFarmAccess()
    await listFarmMemberships()

    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      1,
      '/api/v1/farms/current/membership',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      2,
      '/api/v1/farms/current/memberships',
      expect.any(Object),
    )
  })

  it('sends each write exactly once with its concise request body', async () => {
    protectedApiRequest.mockImplementation(() =>
      Promise.resolve(jsonResponse({ id: 'membership-id' })),
    )

    await inviteFarmMember(' user@example.com ', ['STAFF'])
    await updateFarmMembershipRoles('membership-id', ['MANAGER'])
    await updateFarmMembershipStatus('membership-id', false)

    expect(protectedApiRequest).toHaveBeenCalledTimes(3)
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      1,
      '/api/v1/farms/current/memberships/invitations',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'user@example.com', roles: ['STAFF'] }),
      }),
    )
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      2,
      '/api/v1/farms/current/memberships/membership-id/roles',
      expect.objectContaining({ method: 'PUT' }),
    )
    expect(protectedApiRequest).toHaveBeenNthCalledWith(
      3,
      '/api/v1/farms/current/memberships/membership-id/status',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ enabled: false }),
      }),
    )
  })

  it('maps safe membership problem codes without exposing raw details', async () => {
    protectedApiRequest.mockResolvedValue(
      jsonResponse(
        { code: 'LAST_ACTIVE_OWNER_REQUIRED', detail: 'database detail' },
        409,
      ),
    )

    const error = await updateFarmMembershipStatus(
      'membership-id',
      false,
    ).catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(FarmMembershipApiError)
    expect(error).toMatchObject({
      status: 409,
      code: 'LAST_ACTIVE_OWNER_REQUIRED',
    })
    expect((error as Error).message).not.toContain('database detail')
  })
})

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
