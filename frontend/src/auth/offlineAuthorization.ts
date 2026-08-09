import { POULTRYFLOW_ROLES, type PoultryFlowRole } from './roles'

export type OfflineAuthorizationSnapshot = Readonly<{
  subject: string
  roles: readonly PoultryFlowRole[]
}>

let snapshot: OfflineAuthorizationSnapshot | undefined

export function recordOfflineAuthorization(
  subject: string,
  roles: readonly PoultryFlowRole[],
): void {
  if (!subject) {
    clearOfflineAuthorization()
    return
  }

  const allowedRoles = POULTRYFLOW_ROLES.filter((role) => roles.includes(role))
  snapshot = Object.freeze({
    subject,
    roles: Object.freeze(allowedRoles),
  })
}

export function getOfflineAuthorizationSnapshot():
  OfflineAuthorizationSnapshot | undefined {
  return snapshot
    ? { subject: snapshot.subject, roles: [...snapshot.roles] }
    : undefined
}

export function offlineSnapshotHasAnyRole(
  requiredRoles: readonly PoultryFlowRole[],
): boolean {
  return requiredRoles.some((role) => snapshot?.roles.includes(role) === true)
}

export function clearOfflineAuthorization(): void {
  snapshot = undefined
}
