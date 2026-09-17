import type { PropsWithChildren, ReactNode } from 'react'
import type { PoultryFlowRole } from '../../auth/roles'
import { useFarmAccess } from './useFarmAccess'

type RequireFarmRoleProps = PropsWithChildren<{
  anyOf: readonly PoultryFlowRole[]
  fallback?: ReactNode
}>

export function RequireFarmRole({
  anyOf,
  children,
  fallback = null,
}: RequireFarmRoleProps) {
  const { authentication, query } = useFarmAccess()

  if (
    authentication.status !== 'authenticated' ||
    !authentication.subject ||
    !query.data ||
    query.data.status !== 'ACTIVE' ||
    !anyOf.some((role) => query.data?.roles.includes(role))
  ) {
    return fallback
  }

  return children
}
