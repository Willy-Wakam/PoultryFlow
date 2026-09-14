export const POULTRYFLOW_ROLES = [
  'OWNER',
  'MANAGER',
  'STAFF',
  'ACCOUNTANT',
  'VIEWER',
] as const

export type PoultryFlowRole = (typeof POULTRYFLOW_ROLES)[number]

const knownRoles = new Set<string>(POULTRYFLOW_ROLES)

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null
    ? (value as Record<string, unknown>)
    : undefined
}

export function extractPoultryFlowRoles(
  tokenClaims: unknown,
): readonly PoultryFlowRole[] {
  const claims = asRecord(tokenClaims)
  const resourceAccess = asRecord(claims?.resource_access)
  const apiAccess = asRecord(resourceAccess?.['poultryflow-api'])
  const tokenRoles = apiAccess?.roles

  if (!Array.isArray(tokenRoles)) {
    return []
  }

  const assignedRoles = new Set<PoultryFlowRole>()
  for (const role of tokenRoles) {
    if (typeof role === 'string' && knownRoles.has(role)) {
      assignedRoles.add(role as PoultryFlowRole)
    }
  }

  return POULTRYFLOW_ROLES.filter((role) => assignedRoles.has(role))
}
