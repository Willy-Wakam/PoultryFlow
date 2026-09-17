import { z } from 'zod'

export const FARM_LOCATION_TYPES = ['HOUSE', 'PEN', 'STORAGE', 'OTHER'] as const

export type FarmLocationType = (typeof FARM_LOCATION_TYPES)[number]
export type FarmLocationStatus = 'ACTIVE' | 'INACTIVE'

export type FarmLocation = {
  id: string
  farmId: string
  name: string
  type: FarmLocationType
  status: FarmLocationStatus
  version: number
  createdAt: string
  updatedAt: string
}

export const farmLocationSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Name is required')
    .max(120, 'Name must be at most 120 characters'),
  type: z.enum(FARM_LOCATION_TYPES),
})

export type FarmLocationForm = z.infer<typeof farmLocationSchema>

export const DEFAULT_FARM_LOCATION: FarmLocationForm = {
  name: '',
  type: 'HOUSE',
}

export function locationTypeName(type: FarmLocationType) {
  return type.charAt(0) + type.slice(1).toLowerCase()
}
