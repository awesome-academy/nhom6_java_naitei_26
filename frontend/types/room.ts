import type { Amenity, RoomTypeImage } from "@/types/room-type"

export type RoomView = "SEA" | "CITY" | "GARDEN" | "POOL" | "MOUNTAIN" | "NONE"

export type HousekeepingStatus = "CLEAN" | "DIRTY" | "CLEANING" | "INSPECTED"

export type RoomOperationalStatus =
  | "ACTIVE"
  | "MAINTENANCE"
  | "OUT_OF_SERVICE"
  | "RENOVATION"

export interface Room {
  roomId: number
  roomNumber: string
  roomTypeCode: string
  roomTypeName: string
  viewType: RoomView
  floor: number | null
  operationalStatus: RoomOperationalStatus
  housekeepingStatus: HousekeepingStatus
  priceOverride: number | null
  isActive: boolean
  amenities: Amenity[]
  images: RoomTypeImage[]
  createdAt: string
  updatedAt: string
}

export interface RoomCreateRequest {
  roomNumber: string
  roomTypeCode: string
  viewType: RoomView
  floor: number | null
  priceOverride: number | null
}

export type RoomUpdateRequest = Omit<RoomCreateRequest, "roomNumber">
