import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import {
  DEFAULT_FARM_LOCATION,
  FARM_LOCATION_TYPES,
  farmLocationSchema,
  locationTypeName,
  type FarmLocation,
  type FarmLocationForm,
  type FarmLocationType,
} from './farmLocation'
import {
  createFarmLocation,
  FarmLocationApiError,
  listFarmLocations,
  updateFarmLocation,
  updateFarmLocationStatus,
} from './farmLocationApi'

const locationsQueryKey = ['farm-locations', 'current', 'all'] as const

export function FarmLocationAdministrationPanel() {
  const queryClient = useQueryClient()
  const [feedback, setFeedback] = useState<string>()
  const locations = useQuery({
    queryKey: locationsQueryKey,
    queryFn: () => listFarmLocations(true),
    retry: false,
  })
  const form = useForm<FarmLocationForm>({
    resolver: zodResolver(farmLocationSchema),
    defaultValues: DEFAULT_FARM_LOCATION,
  })
  const selectedType = useWatch({ control: form.control, name: 'type' })
  const creation = useMutation({
    mutationFn: (values: FarmLocationForm) => createFarmLocation(values),
    retry: false,
    onSuccess(created) {
      queryClient.setQueryData<FarmLocation[]>(locationsQueryKey, (current) =>
        [...(current ?? []), created].sort(compareLocations),
      )
      form.reset(DEFAULT_FARM_LOCATION)
      setFeedback('Location created.')
    },
    onError(error) {
      setFeedback(locationErrorMessage(error))
    },
  })

  return (
    <section
      className="location-administration"
      aria-labelledby="farm-locations-title"
    >
      <div className="section-heading">
        <div>
          <p className="section-label">Farm layout</p>
          <h2 id="farm-locations-title">Locations and houses</h2>
        </div>
      </div>

      <form
        className="location-create-form"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setFeedback(undefined)
          creation.mutate(values)
        })}
      >
        <div className="form-field">
          <label htmlFor="location-name">Location name</label>
          <input id="location-name" {...form.register('name')} />
          {form.formState.errors.name?.message ? (
            <p className="field-error" role="alert">
              {form.formState.errors.name.message}
            </p>
          ) : null}
        </div>
        <LocationTypeSelect
          id="location-type"
          value={selectedType}
          onChange={(value) => form.setValue('type', value)}
        />
        <div className="form-actions">
          <button type="submit" disabled={creation.isPending}>
            {creation.isPending ? 'Creating...' : 'Create location'}
          </button>
          {feedback ? (
            <p
              className={`form-feedback ${creation.isError ? 'is-error' : 'is-success'}`}
              role={creation.isError ? 'alert' : 'status'}
            >
              {feedback}
            </p>
          ) : null}
        </div>
      </form>

      {locations.isPending ? <p role="status">Loading locations...</p> : null}
      {locations.isError ? (
        <div className="location-load-error">
          <p role="alert">Farm locations could not be loaded.</p>
          <button
            className="secondary-button"
            type="button"
            onClick={() => void locations.refetch()}
          >
            Try again
          </button>
        </div>
      ) : null}
      {locations.data ? (
        <div className="location-list" aria-label="Farm locations">
          {locations.data.length === 0 ? (
            <p className="empty-state">No farm locations configured.</p>
          ) : (
            locations.data.map((location) => (
              <FarmLocationRow key={location.id} location={location} />
            ))
          )}
        </div>
      ) : null}
    </section>
  )
}

function FarmLocationRow({ location }: { location: FarmLocation }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(location.name)
  const [type, setType] = useState(location.type)
  const [feedback, setFeedback] = useState<string>()

  const replaceLocation = (updated: FarmLocation) => {
    queryClient.setQueryData<FarmLocation[]>(locationsQueryKey, (current) =>
      current?.map((item) => (item.id === updated.id ? updated : item)),
    )
  }
  const edition = useMutation({
    mutationFn: () => updateFarmLocation(location, { name, type }),
    retry: false,
    onSuccess(updated) {
      setName(updated.name)
      setType(updated.type)
      replaceLocation(updated)
      setFeedback('Location saved.')
    },
    onError(error) {
      setFeedback(locationErrorMessage(error))
    },
  })
  const statusChange = useMutation({
    mutationFn: () =>
      updateFarmLocationStatus(
        location,
        location.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      ),
    retry: false,
    onSuccess(updated) {
      replaceLocation(updated)
      setFeedback(
        updated.status === 'ACTIVE'
          ? 'Location reactivated.'
          : 'Location deactivated.',
      )
    },
    onError(error) {
      setFeedback(locationErrorMessage(error))
    },
  })
  const pending = edition.isPending || statusChange.isPending

  return (
    <article
      className={`location-row ${location.status === 'INACTIVE' ? 'is-inactive' : ''}`}
    >
      <div className="location-fields">
        <div className="form-field">
          <label htmlFor={`location-name-${location.id}`}>Name</label>
          <input
            id={`location-name-${location.id}`}
            value={name}
            maxLength={120}
            disabled={pending}
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <LocationTypeSelect
          id={`location-type-${location.id}`}
          value={type}
          disabled={pending}
          onChange={setType}
        />
      </div>
      <div className="location-state">
        <span
          className={`location-status status-${location.status.toLowerCase()}`}
        >
          {location.status.toLowerCase()}
        </span>
        <span className="location-id">ID {location.id}</span>
      </div>
      <div className="location-actions">
        <button
          className="secondary-button"
          type="button"
          disabled={pending || name.trim().length === 0}
          onClick={() => {
            setFeedback(undefined)
            edition.mutate()
          }}
        >
          Save changes
        </button>
        <button
          className={
            location.status === 'ACTIVE' ? 'danger-button' : 'secondary-button'
          }
          type="button"
          disabled={pending}
          onClick={() => {
            setFeedback(undefined)
            statusChange.mutate()
          }}
        >
          {location.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
        </button>
        {feedback ? (
          <p
            className={`form-feedback ${edition.isError || statusChange.isError ? 'is-error' : 'is-success'}`}
            role={edition.isError || statusChange.isError ? 'alert' : 'status'}
          >
            {feedback}
          </p>
        ) : null}
      </div>
    </article>
  )
}

type LocationTypeSelectProps = {
  id: string
  value: FarmLocationType
  disabled?: boolean
  onChange: (value: FarmLocationType) => void
}

function LocationTypeSelect({
  id,
  value,
  disabled = false,
  onChange,
}: LocationTypeSelectProps) {
  return (
    <div className="form-field">
      <label htmlFor={id}>Type</label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as FarmLocationType)}
      >
        {FARM_LOCATION_TYPES.map((option) => (
          <option key={option} value={option}>
            {locationTypeName(option)}
          </option>
        ))}
      </select>
    </div>
  )
}

function compareLocations(left: FarmLocation, right: FarmLocation) {
  return left.name.localeCompare(right.name)
}

function locationErrorMessage(error: unknown) {
  if (error instanceof FarmLocationApiError) {
    if (error.code === 'FARM_LOCATION_NAME_ALREADY_EXISTS') {
      return 'That location name is already in use.'
    }
    if (error.code === 'CONCURRENT_MODIFICATION') {
      return 'This location changed. Refresh and try again.'
    }
  }
  return 'The location change could not be completed.'
}
