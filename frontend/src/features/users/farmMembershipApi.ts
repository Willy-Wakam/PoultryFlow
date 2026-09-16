import type { PoultryFlowRole } from '../../auth/roles'
import { protectedApiRequest } from '../../auth/protectedApi'
import type { FarmAccess, FarmMembership } from './farmMembership'

const CURRENT_ACCESS_PATH = '/api/v1/farms/current/membership'
const MEMBERSHIPS_PATH = '/api/v1/farms/current/memberships'

type ProblemResponse = {
  code?: unknown
  violations?: unknown
}

export class FarmMembershipApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super('The farm membership request could not be completed.')
    this.name = 'FarmMembershipApiError'
  }
}

export async function getCurrentFarmAccess(): Promise<FarmAccess> {
  return requestJson<FarmAccess>(CURRENT_ACCESS_PATH, {
    headers: { Accept: 'application/json' },
  })
}

export async function listFarmMemberships(): Promise<FarmMembership[]> {
  return requestJson<FarmMembership[]>(MEMBERSHIPS_PATH, {
    headers: { Accept: 'application/json' },
  })
}

export async function inviteFarmMember(
  email: string,
  roles: readonly PoultryFlowRole[],
): Promise<FarmMembership> {
  return requestJson<FarmMembership>(`${MEMBERSHIPS_PATH}/invitations`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ email: email.trim(), roles }),
  })
}

export async function updateFarmMembershipRoles(
  membershipId: string,
  roles: readonly PoultryFlowRole[],
): Promise<FarmMembership> {
  return requestJson<FarmMembership>(
    `${MEMBERSHIPS_PATH}/${encodeURIComponent(membershipId)}/roles`,
    {
      method: 'PUT',
      headers: jsonHeaders(),
      body: JSON.stringify({ roles }),
    },
  )
}

export async function updateFarmMembershipStatus(
  membershipId: string,
  enabled: boolean,
): Promise<FarmMembership> {
  return requestJson<FarmMembership>(
    `${MEMBERSHIPS_PATH}/${encodeURIComponent(membershipId)}/status`,
    {
      method: 'PUT',
      headers: jsonHeaders(),
      body: JSON.stringify({ enabled }),
    },
  )
}

async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await protectedApiRequest(path, init)
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as T
}

async function toApiError(response: Response) {
  let problem: ProblemResponse = {}
  try {
    problem = (await response.clone().json()) as ProblemResponse
  } catch {
    // The UI deliberately maps malformed server errors to a stable fallback.
  }
  return new FarmMembershipApiError(
    response.status,
    typeof problem.code === 'string' ? problem.code : 'HTTP_ERROR',
  )
}

function jsonHeaders() {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  }
}
