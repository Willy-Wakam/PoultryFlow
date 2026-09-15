import { protectedApiRequest } from '../../auth/protectedApi'
import type { FarmProfile, FarmProfileRequest } from './farmProfile'

const FARM_PROFILE_PATH = '/api/v1/farms/current'

export type ApiValidationViolation = {
  field: string
  message: string
}

type ProblemResponse = {
  code?: unknown
  detail?: unknown
  violations?: unknown
}

export class FarmProfileApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly violations: readonly ApiValidationViolation[] = [],
  ) {
    super('The farm profile request could not be completed.')
    this.name = 'FarmProfileApiError'
  }
}

export class FarmProfileNotConfiguredError extends FarmProfileApiError {
  constructor() {
    super(404, 'FARM_PROFILE_NOT_CONFIGURED')
    this.name = 'FarmProfileNotConfiguredError'
  }
}

export async function getFarmProfile(): Promise<FarmProfile> {
  const response = await protectedApiRequest(FARM_PROFILE_PATH, {
    headers: { Accept: 'application/json' },
  })
  if (response.status === 404) {
    const problem = await readProblem(response)
    if (problem.code === 'FARM_PROFILE_NOT_CONFIGURED') {
      throw new FarmProfileNotConfiguredError()
    }
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as FarmProfile
}

export async function saveFarmProfile(
  profile: FarmProfileRequest,
): Promise<FarmProfile> {
  const response = await protectedApiRequest(FARM_PROFILE_PATH, {
    method: 'PUT',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(profile),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as FarmProfile
}

async function toApiError(response: Response) {
  const problem = await readProblem(response)
  return new FarmProfileApiError(
    response.status,
    typeof problem.code === 'string' ? problem.code : 'HTTP_ERROR',
    validationViolations(problem.violations),
  )
}

async function readProblem(response: Response): Promise<ProblemResponse> {
  try {
    return (await response.clone().json()) as ProblemResponse
  } catch {
    return {}
  }
}

function validationViolations(value: unknown): ApiValidationViolation[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item) => {
    if (
      typeof item === 'object' &&
      item !== null &&
      typeof Reflect.get(item, 'field') === 'string' &&
      typeof Reflect.get(item, 'message') === 'string'
    ) {
      return [
        {
          field: Reflect.get(item, 'field') as string,
          message: Reflect.get(item, 'message') as string,
        },
      ]
    }
    return []
  })
}
