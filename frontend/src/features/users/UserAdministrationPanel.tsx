import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { POULTRYFLOW_ROLES, type PoultryFlowRole } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import {
  DEFAULT_INVITATION,
  inviteFarmMemberSchema,
  type FarmMembership,
  type InviteFarmMemberForm,
} from './farmMembership'
import {
  FarmMembershipApiError,
  inviteFarmMember,
  listFarmMemberships,
  updateFarmMembershipRoles,
  updateFarmMembershipStatus,
} from './farmMembershipApi'
import { farmAccessQueryKey } from './useFarmAccess'

const membershipsQueryKey = ['farm-memberships', 'current'] as const

export function UserAdministrationPanel() {
  const queryClient = useQueryClient()
  const authentication = useAuth()
  const [feedback, setFeedback] = useState<string>()
  const memberships = useQuery({
    queryKey: membershipsQueryKey,
    queryFn: listFarmMemberships,
    retry: false,
  })
  const form = useForm<InviteFarmMemberForm>({
    resolver: zodResolver(inviteFarmMemberSchema),
    defaultValues: DEFAULT_INVITATION,
  })
  const invitationRoles = useWatch({ control: form.control, name: 'roles' })
  const invitation = useMutation({
    mutationFn: (values: InviteFarmMemberForm) =>
      inviteFarmMember(values.email, values.roles),
    retry: false,
    async onSuccess() {
      form.reset(DEFAULT_INVITATION)
      setFeedback('Invitation recorded.')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: membershipsQueryKey }),
        queryClient.invalidateQueries({
          queryKey: farmAccessQueryKey(authentication.subject),
        }),
      ])
    },
    onError(error) {
      setFeedback(membershipErrorMessage(error))
    },
  })

  return (
    <section className="user-administration" aria-labelledby="farm-users-title">
      <div className="section-heading">
        <div>
          <p className="section-label">Access</p>
          <h2 id="farm-users-title">Farm users</h2>
        </div>
      </div>

      <form
        className="invitation-form"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setFeedback(undefined)
          invitation.mutate(values)
        })}
      >
        <div className="form-field">
          <label htmlFor="invite-email">Email address</label>
          <input
            id="invite-email"
            type="email"
            autoComplete="email"
            {...form.register('email')}
          />
          {form.formState.errors.email?.message ? (
            <p className="field-error" role="alert">
              {form.formState.errors.email.message}
            </p>
          ) : null}
        </div>
        <RoleSelector
          legend="Initial roles"
          name="roles"
          selected={invitationRoles}
          onToggle={(role, checked) => {
            const current = form.getValues('roles')
            form.setValue(
              'roles',
              checked
                ? [...new Set([...current, role])]
                : current.filter((value) => value !== role),
              { shouldDirty: true, shouldValidate: true },
            )
          }}
        />
        {form.formState.errors.roles?.message ? (
          <p className="field-error" role="alert">
            {form.formState.errors.roles.message}
          </p>
        ) : null}
        <div className="form-actions">
          <button type="submit" disabled={invitation.isPending}>
            {invitation.isPending ? 'Inviting...' : 'Invite user'}
          </button>
          {feedback ? (
            <p
              className={`form-feedback ${invitation.isError ? 'is-error' : 'is-success'}`}
              role={invitation.isError ? 'alert' : 'status'}
            >
              {feedback}
            </p>
          ) : null}
        </div>
      </form>

      {memberships.isPending ? (
        <p role="status">Loading farm users...</p>
      ) : null}
      {memberships.isError ? (
        <div className="membership-load-error">
          <p role="alert">Farm users could not be loaded.</p>
          <button
            className="secondary-button"
            type="button"
            onClick={() => void memberships.refetch()}
          >
            Try again
          </button>
        </div>
      ) : null}
      {memberships.data ? (
        <div className="membership-list" aria-label="Farm memberships">
          {memberships.data.length === 0 ? (
            <p className="empty-state">
              No farm memberships have been recorded.
            </p>
          ) : (
            memberships.data.map((membership) => (
              <MembershipRow key={membership.id} membership={membership} />
            ))
          )}
        </div>
      ) : null}
    </section>
  )
}

function MembershipRow({ membership }: { membership: FarmMembership }) {
  const queryClient = useQueryClient()
  const [roles, setRoles] = useState<readonly PoultryFlowRole[]>(
    membership.roles,
  )
  const [feedback, setFeedback] = useState<string>()

  const replaceMembership = (updated: FarmMembership) => {
    queryClient.setQueryData<FarmMembership[]>(membershipsQueryKey, (current) =>
      current?.map((item) => (item.id === updated.id ? updated : item)),
    )
  }
  const roleUpdate = useMutation({
    mutationFn: () => updateFarmMembershipRoles(membership.id, roles),
    retry: false,
    async onSuccess(updated) {
      setRoles(updated.roles)
      replaceMembership(updated)
      setFeedback('Roles saved.')
      await queryClient.invalidateQueries({ queryKey: ['farm-access'] })
    },
    onError(error) {
      setFeedback(membershipErrorMessage(error))
    },
  })
  const statusUpdate = useMutation({
    mutationFn: (enabled: boolean) =>
      updateFarmMembershipStatus(membership.id, enabled),
    retry: false,
    async onSuccess(updated) {
      replaceMembership(updated)
      setFeedback(
        updated.status === 'DISABLED' ? 'User disabled.' : 'User re-enabled.',
      )
      await queryClient.invalidateQueries({ queryKey: ['farm-access'] })
    },
    onError(error) {
      setFeedback(membershipErrorMessage(error))
    },
  })
  const pending = roleUpdate.isPending || statusUpdate.isPending

  return (
    <article className="membership-row">
      <div className="membership-identity">
        <strong>{membership.email}</strong>
        <span
          className={`membership-status status-${membership.status.toLowerCase()}`}
        >
          {membership.status.toLowerCase()}
        </span>
      </div>
      <RoleSelector
        legend={`Roles for ${membership.email}`}
        name={`roles-${membership.id}`}
        selected={roles}
        disabled={pending}
        onToggle={(role, checked) =>
          setRoles((current) =>
            checked
              ? [...new Set([...current, role])]
              : current.filter((value) => value !== role),
          )
        }
      />
      <div className="membership-actions">
        <button
          className="secondary-button"
          type="button"
          disabled={pending || roles.length === 0}
          onClick={() => {
            setFeedback(undefined)
            roleUpdate.mutate()
          }}
        >
          Save roles
        </button>
        <button
          className={
            membership.status === 'DISABLED'
              ? 'secondary-button'
              : 'danger-button'
          }
          type="button"
          disabled={pending}
          onClick={() => {
            setFeedback(undefined)
            statusUpdate.mutate(membership.status === 'DISABLED')
          }}
        >
          {membership.status === 'DISABLED' ? 'Re-enable' : 'Disable'}
        </button>
        {feedback ? (
          <p
            className={`form-feedback ${roleUpdate.isError || statusUpdate.isError ? 'is-error' : 'is-success'}`}
            role={
              roleUpdate.isError || statusUpdate.isError ? 'alert' : 'status'
            }
          >
            {feedback}
          </p>
        ) : null}
      </div>
    </article>
  )
}

type RoleSelectorProps = {
  legend: string
  name: string
  selected: readonly PoultryFlowRole[]
  disabled?: boolean
  onToggle: (role: PoultryFlowRole, checked: boolean) => void
}

function RoleSelector({
  legend,
  name,
  selected,
  disabled = false,
  onToggle,
}: RoleSelectorProps) {
  return (
    <fieldset className="role-selector">
      <legend>{legend}</legend>
      <div className="role-options">
        {POULTRYFLOW_ROLES.map((role) => (
          <label key={role}>
            <input
              type="checkbox"
              name={name}
              value={role}
              checked={selected.includes(role)}
              disabled={disabled}
              onChange={(event) => onToggle(role, event.target.checked)}
            />
            {roleName(role)}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

function roleName(role: PoultryFlowRole) {
  return role.charAt(0) + role.slice(1).toLowerCase()
}

function membershipErrorMessage(error: unknown) {
  if (error instanceof FarmMembershipApiError) {
    if (error.code === 'LAST_ACTIVE_OWNER_REQUIRED') {
      return 'At least one active farm owner must remain.'
    }
    if (error.code === 'FARM_MEMBERSHIP_ALREADY_EXISTS') {
      return 'That email already has a membership or invitation.'
    }
    if (error.code === 'CONCURRENT_MODIFICATION') {
      return 'This membership changed. Refresh and try again.'
    }
  }
  return 'The membership change could not be completed.'
}
