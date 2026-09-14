import type { PropsWithChildren, ReactNode } from 'react'
import type { PoultryFlowRole } from './roles'
import { useAuth } from './useAuth'

type RequireRoleProps = PropsWithChildren<{
  anyOf: readonly PoultryFlowRole[]
  fallback?: ReactNode
}>

export function RequireRole({
  anyOf,
  children,
  fallback = null,
}: RequireRoleProps) {
  const authentication = useAuth()

  if (
    authentication.status !== 'authenticated' ||
    !authentication.hasAnyRole(anyOf)
  ) {
    return fallback
  }

  return children
}
