import { z } from 'zod'

function isSupportedTimeZone(value: string) {
  try {
    new Intl.DateTimeFormat('en', { timeZone: value }).format()
    return true
  } catch {
    return false
  }
}

export const farmProfileSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Farm name is required')
    .max(160, 'Farm name must be at most 160 characters'),
  contactEmail: z
    .string()
    .trim()
    .max(254, 'Email must be at most 254 characters')
    .refine(
      (value) => value === '' || z.email().safeParse(value).success,
      'Enter a valid email address',
    ),
  contactPhone: z
    .string()
    .trim()
    .max(40, 'Phone must be at most 40 characters'),
  timezone: z
    .string()
    .trim()
    .min(1, 'Timezone is required')
    .max(64, 'Timezone must be at most 64 characters')
    .refine(isSupportedTimeZone, 'Enter a valid IANA timezone'),
  countryCode: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{2}$/, 'Enter a 2-letter country code'),
  currencyCode: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{3}$/, 'Enter a 3-letter currency code'),
})

export type FarmProfileFormValues = z.infer<typeof farmProfileSchema>

export type FarmProfile = {
  id: string
  name: string
  contactEmail: string | null
  contactPhone: string | null
  timezone: string
  countryCode: string
  currencyCode: string
  version: number
  createdAt: string
  updatedAt: string
}

export type FarmProfileRequest = {
  name: string
  contactEmail: string | null
  contactPhone: string | null
  timezone: string
  countryCode: string
  currencyCode: string
}

export const FARM_PROFILE_SETUP_DEFAULTS: FarmProfileFormValues = {
  name: '',
  contactEmail: '',
  contactPhone: '',
  timezone: 'Africa/Douala',
  countryCode: 'CM',
  currencyCode: 'XAF',
}

export function toFarmProfileRequest(
  values: FarmProfileFormValues,
): FarmProfileRequest {
  const email = values.contactEmail.trim()
  const phone = values.contactPhone.trim()
  return {
    name: values.name.trim(),
    contactEmail: email === '' ? null : email,
    contactPhone: phone === '' ? null : phone,
    timezone: values.timezone.trim(),
    countryCode: values.countryCode.trim().toUpperCase(),
    currencyCode: values.currencyCode.trim().toUpperCase(),
  }
}

export function toFarmProfileFormValues(
  profile: FarmProfile,
): FarmProfileFormValues {
  return {
    name: profile.name,
    contactEmail: profile.contactEmail ?? '',
    contactPhone: profile.contactPhone ?? '',
    timezone: profile.timezone,
    countryCode: profile.countryCode,
    currencyCode: profile.currencyCode,
  }
}
