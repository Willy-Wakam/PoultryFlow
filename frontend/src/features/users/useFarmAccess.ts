import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../auth/useAuth'
import { getCurrentFarmAccess } from './farmMembershipApi'

export const farmAccessQueryKey = (subject: string | undefined) =>
  ['farm-access', subject ?? 'unknown'] as const

export function useFarmAccess() {
  const authentication = useAuth()
  const query = useQuery({
    queryKey: farmAccessQueryKey(authentication.subject),
    queryFn: getCurrentFarmAccess,
    enabled:
      authentication.status === 'authenticated' &&
      authentication.subject !== undefined,
    retry: false,
  })

  return { authentication, query }
}
