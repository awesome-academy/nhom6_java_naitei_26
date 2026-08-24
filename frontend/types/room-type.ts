import type { CancellationPolicy } from "@/types/cancellation-policy"

export type BedType =
  | "SINGLE"
  | "DOUBLE"
  | "QUEEN"
  | "KING"
  | "SOFA_BED"
  | "BUNK"

export type AmenityCategory = "ROOM" | "BATHROOM" | "TECH" | "SERVICE"

export interface RoomTypeBed {
  bedType: BedType
  quantity: number
}

export interface Amenity {
  code: string
  name: string
  icon: string | null
  category: AmenityCategory
  isFilterable: boolean
  sortOrder: number
  createdAt?: string
  updatedAt?: string
}

export interface RoomTypeImage {
  imageId: string
  downloadUrl: string
  downloadUrlExpiresAt: string
  altText: string
  isPrimary: boolean
  sortOrder: number
}

export interface RoomType {
  code: string
  name: string
  slug: string
  description: string | null
  bedCount: number
  maxOccupancy: number
  maxAdults: number
  maxChildren: number
  basePrice: number
  currency: string
  extraBedPrice: number | null
  sizeSqm: number | null
  isActive: boolean
  sortOrder: number
  cancellationPolicy: CancellationPolicy | null
  beds: RoomTypeBed[]
  amenities: Amenity[]
  images: RoomTypeImage[]
  createdAt: string
  updatedAt: string
}

export interface RoomTypeStats {
  total: number
  active: number
  deactivated: number
}

export interface RoomTypeCreateRequest {
  code: string
  name: string
  description: string | null
  maxOccupancy: number
  maxAdults: number
  maxChildren: number
  basePrice: number
  currency: string
  extraBedPrice: number | null
  sizeSqm: number | null
  isActive: boolean
  sortOrder: number
  cancellationPolicyCode: string | null
  beds: RoomTypeBed[]
  amenityCodes: string[]
}

export type RoomTypeUpdateRequest = Omit<
  RoomTypeCreateRequest,
  "code" | "beds" | "amenityCodes"
>

export interface ImageUploadUrlResponse {
  uploadId: string
  uploadUrl: string
  requiredHeaders: Record<string, string>
  expiresAt: string
}
