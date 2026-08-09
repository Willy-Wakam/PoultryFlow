import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearOfflineAuthorization,
  getOfflineAuthorizationSnapshot,
  offlineSnapshotHasAnyRole,
  recordOfflineAuthorization,
} from './offlineAuthorization'

describe('offline authorization snapshot', () => {
  beforeEach(clearOfflineAuthorization)

  it('keeps only the subject and allowlisted PoultryFlow roles in memory', () => {
    recordOfflineAuthorization('user-123', ['OWNER', 'OWNER', 'STAFF'])

    expect(getOfflineAuthorizationSnapshot()).toEqual({
      subject: 'user-123',
      roles: ['OWNER', 'STAFF'],
    })
    expect(Object.keys(getOfflineAuthorizationSnapshot() ?? {})).toEqual([
      'subject',
      'roles',
    ])
  })

  it('supports future offline role checks without authenticating online UI', () => {
    recordOfflineAuthorization('user-123', ['ACCOUNTANT'])

    expect(offlineSnapshotHasAnyRole(['OWNER', 'ACCOUNTANT'])).toBe(true)
    expect(offlineSnapshotHasAnyRole(['OWNER', 'MANAGER'])).toBe(false)
  })

  it('replaces a previous user snapshot instead of combining permissions', () => {
    recordOfflineAuthorization('previous-user', ['OWNER'])

    recordOfflineAuthorization('current-user', ['VIEWER'])

    expect(getOfflineAuthorizationSnapshot()).toEqual({
      subject: 'current-user',
      roles: ['VIEWER'],
    })
  })

  it('clears the snapshot explicitly', () => {
    recordOfflineAuthorization('user-123', ['VIEWER'])

    clearOfflineAuthorization()

    expect(getOfflineAuthorizationSnapshot()).toBeUndefined()
  })
})
