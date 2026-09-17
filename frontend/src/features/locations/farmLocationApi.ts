import { protectedApiRequest } from '../../auth/protectedApi'
import type {
  FarmLocation,
  FarmLocationForm,
  FarmLocationStatus,
} from './farmLocation'

const LOCATIONS_PATH = '/api/v1/farms/current/locations'

type ProblemResponse = {
  code?: unknown
}

export class FarmLocationApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super('The farm location request could not be completed.')
    this.name = 'FarmLocationApiError'
  }
}

export async function listFarmLocations(
  includeInactive = false,
): Promise<FarmLocation[]> {
  const suffix = includeInactive ? '?includeInactive=true' : ''
  return requestJson<FarmLocation[]>(`${LOCATIONS_PATH}${suffix}`, {
    headers: { Accept: 'application/json' },
  })
}

export async function createFarmLocation(
  values: FarmLocationForm,
): Promise<FarmLocation> {
  return requestJson<FarmLocation>(LOCATIONS_PATH, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ ...values, name: values.name.trim() }),
  })
}

export async function updateFarmLocation(
  location: Pick<FarmLocation, 'id' | 'version'>,
  values: FarmLocationForm,
): Promise<FarmLocation> {
  return requestJson<FarmLocation>(
    `${LOCATIONS_PATH}/${encodeURIComponent(location.id)}`,
    {
      method: 'PUT',
      headers: jsonHeaders(),
      body: JSON.stringify({
        ...values,
        name: values.name.trim(),
        version: location.version,
      }),
    },
  )
}

export async function updateFarmLocationStatus(
  location: Pick<FarmLocation, 'id' | 'version'>,
  status: FarmLocationStatus,
): Promise<FarmLocation> {
  return requestJson<FarmLocation>(
    `${LOCATIONS_PATH}/${encodeURIComponent(location.id)}/status`,
    {
      method: 'PUT',
      headers: jsonHeaders(),
      body: JSON.stringify({ status, version: location.version }),
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
    // Malformed server errors are deliberately mapped to a stable fallback.
  }
  return new FarmLocationApiError(
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
