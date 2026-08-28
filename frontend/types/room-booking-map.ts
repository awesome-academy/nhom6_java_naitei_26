import type { BookingRoomStatus, BookingStatus } from "@/types/booking"
import type { HousekeepingStatus, RoomOperationalStatus, RoomView } from "@/types/room"

export type RoomTimelineEventType = "BOOKING" | "ROOM_STATUS_BLOCK"

export type RoomBlockType =
  | "MAINTENANCE"
  | "RENOVATION"
  | "OUT_OF_SERVICE"
  | "INTERNAL_USE"
  | "DEEP_CLEANING"

export interface RoomTimelineEvent {
  type: RoomTimelineEventType
  startDate: string
  endDate: string
  label: string
  bookingPublicId: string | null
  bookingCode: string | null
  bookingStatus: BookingStatus | null
  bookingRoomStatus: BookingRoomStatus | null
  blockType: RoomBlockType | null
  reason: string | null
}

export interface RoomBookingMapRoom {
  roomId: number
  roomNumber: string
  roomTypeCode: string | null
  roomTypeName: string | null
  viewType: RoomView
  floor: number | null
  operationalStatus: RoomOperationalStatus
  housekeepingStatus: HousekeepingStatus
  maxOccupancy: number | null
  selectable: boolean
  unavailableReason: string | null
  timeline: RoomTimelineEvent[]
}
