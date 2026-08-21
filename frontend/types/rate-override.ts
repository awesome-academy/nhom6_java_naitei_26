export interface RateOverride {
  id: number
  roomTypeCode: string | null
  roomTypeName: string | null
  roomNumber: string | null
  name: string
  startDate: string
  endDate: string
  price: number
  weekdays: number[] | null
  priority: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface RoomTypeRateOverrideCreateRequest {
  name: string
  startDate: string
  endDate: string
  price: number
  weekdays: number[] | null
  priority: number
}
