import { apiClient } from "@/lib/api/client"
import type {
  RateOverride,
  RoomTypeRateOverrideCreateRequest,
  RateOverrideUpdateRequest,
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

export function updateRateOverride(
  id: number,
  request: RateOverrideUpdateRequest
): Promise<RateOverride> {
  return apiClient.put<RateOverride>(`/api/rate-overrides/${id}`, request)
}

export function deleteRateOverride(id: number): Promise<void> {
  return apiClient.delete<void>(`/api/rate-overrides/${id}`)
}
