import { describe, expect, it } from 'vitest'
import { extractPoultryFlowRoles } from './roles'

describe('PoultryFlow token roles', () => {
  it('reads known roles only from the PoultryFlow API client claim', () => {
    expect(
      extractPoultryFlowRoles({
        realm_access: { roles: ['OWNER'] },
        resource_access: {
          'poultryflow-api': {
            roles: ['VIEWER', 'UNKNOWN_ROLE', 'STAFF', 'VIEWER'],
          },
          'another-client': { roles: ['MANAGER'] },
        },
      }),
    ).toEqual(['STAFF', 'VIEWER'])
  })

  it('returns no roles for malformed or missing API client claims', () => {
    expect(extractPoultryFlowRoles(undefined)).toEqual([])
    expect(
      extractPoultryFlowRoles({
        resource_access: { 'poultryflow-api': { roles: 'OWNER' } },
      }),
    ).toEqual([])
  })
})
