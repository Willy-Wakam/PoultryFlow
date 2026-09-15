import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import {
  FARM_PROFILE_SETUP_DEFAULTS,
  farmProfileSchema,
  toFarmProfileFormValues,
  toFarmProfileRequest,
  type FarmProfile,
  type FarmProfileFormValues,
} from './farmProfile'
import {
  FarmProfileApiError,
  FarmProfileNotConfiguredError,
  getFarmProfile,
  saveFarmProfile,
} from './farmProfileApi'

const farmProfileQueryKey = ['farm-profile', 'current'] as const

const formFields = new Set<keyof FarmProfileFormValues>([
  'name',
  'contactEmail',
  'contactPhone',
  'timezone',
  'countryCode',
  'currencyCode',
])

export function FarmProfilePanel() {
  const queryClient = useQueryClient()
  const [feedback, setFeedback] = useState<string>()
  const profileQuery = useQuery({
    queryKey: farmProfileQueryKey,
    queryFn: getFarmProfile,
    retry: false,
  })
  const form = useForm<FarmProfileFormValues>({
    resolver: zodResolver(farmProfileSchema),
    defaultValues: FARM_PROFILE_SETUP_DEFAULTS,
  })

  useEffect(() => {
    if (profileQuery.data) {
      form.reset(toFarmProfileFormValues(profileQuery.data))
    } else if (profileQuery.error instanceof FarmProfileNotConfiguredError) {
      form.reset(FARM_PROFILE_SETUP_DEFAULTS)
    }
  }, [form, profileQuery.data, profileQuery.error])

  const saveMutation = useMutation({
    mutationFn: saveFarmProfile,
    retry: false,
    onSuccess(profile: FarmProfile) {
      queryClient.setQueryData(farmProfileQueryKey, profile)
      form.reset(toFarmProfileFormValues(profile))
      setFeedback('Farm profile saved.')
    },
    onError(error) {
      setFeedback(
        'Farm profile could not be saved. Your changes are still here.',
      )
      if (error instanceof FarmProfileApiError) {
        for (const violation of error.violations) {
          if (formFields.has(violation.field as keyof FarmProfileFormValues)) {
            form.setError(violation.field as keyof FarmProfileFormValues, {
              type: 'server',
              message: violation.message,
            })
          }
        }
      }
    },
  })

  if (profileQuery.isPending) {
    return (
      <section className="farm-profile" aria-labelledby="farm-profile-title">
        <h2 id="farm-profile-title">Farm profile</h2>
        <p role="status">Loading farm profile...</p>
      </section>
    )
  }

  if (
    profileQuery.isError &&
    !(profileQuery.error instanceof FarmProfileNotConfiguredError)
  ) {
    return (
      <section className="farm-profile" aria-labelledby="farm-profile-title">
        <h2 id="farm-profile-title">Farm profile</h2>
        <p className="form-feedback is-error" role="alert">
          Farm profile could not be loaded. Check your connection and try again.
        </p>
        <button
          className="secondary-button"
          type="button"
          onClick={() => void profileQuery.refetch()}
        >
          Try again
        </button>
      </section>
    )
  }

  return (
    <section className="farm-profile" aria-labelledby="farm-profile-title">
      <div className="section-heading">
        <div>
          <p className="section-label">Settings</p>
          <h2 id="farm-profile-title">Farm profile</h2>
        </div>
        {profileQuery.data ? (
          <span className="saved-state">Configured</span>
        ) : (
          <span className="setup-state">Setup required</span>
        )}
      </div>

      <form
        className="farm-profile-form"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setFeedback(undefined)
          saveMutation.mutate(toFarmProfileRequest(values))
        })}
      >
        <FormField
          id="farm-name"
          label="Farm name"
          error={form.formState.errors.name?.message}
        >
          <input
            id="farm-name"
            autoComplete="organization"
            {...form.register('name')}
          />
        </FormField>

        <div className="form-grid">
          <FormField
            id="farm-email"
            label="Contact email"
            error={form.formState.errors.contactEmail?.message}
          >
            <input
              id="farm-email"
              type="email"
              autoComplete="email"
              {...form.register('contactEmail')}
            />
          </FormField>
          <FormField
            id="farm-phone"
            label="Contact phone"
            error={form.formState.errors.contactPhone?.message}
          >
            <input
              id="farm-phone"
              type="tel"
              autoComplete="tel"
              {...form.register('contactPhone')}
            />
          </FormField>
        </div>

        <FormField
          id="farm-timezone"
          label="Timezone"
          error={form.formState.errors.timezone?.message}
        >
          <input id="farm-timezone" {...form.register('timezone')} />
        </FormField>

        <div className="form-grid form-grid-compact">
          <FormField
            id="farm-country"
            label="Country code"
            error={form.formState.errors.countryCode?.message}
          >
            <input
              id="farm-country"
              maxLength={2}
              autoCapitalize="characters"
              {...form.register('countryCode')}
            />
          </FormField>
          <FormField
            id="farm-currency"
            label="Currency code"
            error={form.formState.errors.currencyCode?.message}
          >
            <input
              id="farm-currency"
              maxLength={3}
              autoCapitalize="characters"
              {...form.register('currencyCode')}
            />
          </FormField>
        </div>

        <div className="form-actions">
          <button type="submit" disabled={saveMutation.isPending}>
            {saveMutation.isPending ? 'Saving...' : 'Save profile'}
          </button>
          {feedback ? (
            <p
              className={`form-feedback ${saveMutation.isError ? 'is-error' : 'is-success'}`}
              role={saveMutation.isError ? 'alert' : 'status'}
            >
              {feedback}
            </p>
          ) : null}
        </div>
      </form>
    </section>
  )
}

type FormFieldProps = {
  id: string
  label: string
  error?: string
  children: ReactNode
}

function FormField({ id, label, error, children }: FormFieldProps) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <p className="field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
