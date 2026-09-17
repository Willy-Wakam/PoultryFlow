import { z } from 'zod'
import { POULTRYFLOW_ROLES, type PoultryFlowRole } from '../../auth/roles'

export type FarmMembershipStatus = 'INVITED' | 'ACTIVE' | 'DISABLED'

export type FarmAccess = {
  farmId: string | null
  membershipId: string | null
  roles: PoultryFlowRole[]
  status: FarmMembershipStatus
  bootstrapAuthority: boolean
}

export type FarmMembership = {
  id: string
  farmId: string
  email: string
  roles: PoultryFlowRole[]
  status: FarmMembershipStatus
  version: number
  createdAt: string
  updatedAt: string
}

export const inviteFarmMemberSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Email is required')
    .max(254, 'Email must be at most 254 characters')
    .pipe(z.email('Enter a valid email address')),
  roles: z.array(z.enum(POULTRYFLOW_ROLES)).min(1, 'Select at least one role'),
})

export type InviteFarmMemberForm = z.infer<typeof inviteFarmMemberSchema>

export const DEFAULT_INVITATION: InviteFarmMemberForm = {
  email: '',
  roles: ['STAFF'],
}
