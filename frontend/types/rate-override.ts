export interface RateOverride {
  id: number
  roomTypeCode: string
  roomTypeName: string
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

export interface RateOverrideUpdateRequest extends RoomTypeRateOverrideCreateRequest {
  roomTypeId: number
}
