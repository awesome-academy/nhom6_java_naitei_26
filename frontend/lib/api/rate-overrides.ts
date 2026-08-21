import { apiClient } from "@/lib/api/client"
import type {
  RateOverride,
  RoomTypeRateOverrideCreateRequest,
} from "@/types/rate-override"

export function getActiveRateOverrides(): Promise<RateOverride[]> {
  return apiClient.get<RateOverride[]>("/api/rate-overrides")
}

export function createRoomTypeRateOverride(
  roomTypeCode: string,
  request: RoomTypeRateOverrideCreateRequest
): Promise<RateOverride> {
  return apiClient.post<RateOverride>(
    `/api/rate-overrides/room-types/${encodeURIComponent(roomTypeCode)}`,
    request
  )
}
