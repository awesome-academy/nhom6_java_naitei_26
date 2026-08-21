import type { RoomOperationalStatus } from "@/types/room"

export type RoomBlockType =
  | "MAINTENANCE"
  | "RENOVATION"
  | "OUT_OF_SERVICE"
  | "INTERNAL_USE"
  | "DEEP_CLEANING"

export interface RoomStatusBlock {
  publicId: string
  roomNumber: string
  operationalStatus: RoomOperationalStatus
  blockType: RoomBlockType
  startDate: string
  endDate: string
  reason: string | null
  createdAt: string
  updatedAt: string
}

export interface RoomStatusBlockCreateRequest {
  roomNumber: string
  blockType: RoomBlockType
  startDate: string
  endDate: string
  reason: string | null
}
