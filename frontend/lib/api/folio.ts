import { apiClient } from "@/lib/api/client"
import type { FolioChargeResponse } from "@/types/booking-staff"
import type {
  FolioChargeCreateRequest,
  FolioChargeVoidRequest,
  ServiceItemOption,
} from "@/types/folio"

export function getActiveServiceItems(): Promise<ServiceItemOption[]> {
  return apiClient.get<ServiceItemOption[]>("/api/service-items")
}

export function createFolioCharge(
  bookingPublicId: string,
  request: FolioChargeCreateRequest
): Promise<FolioChargeResponse> {
  return apiClient.post<FolioChargeResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/folio-charges`,
    request
  )
}

export function voidFolioCharge(
  bookingPublicId: string,
  chargeId: number,
  request: FolioChargeVoidRequest
): Promise<FolioChargeResponse> {
  return apiClient.patch<FolioChargeResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/folio-charges/${chargeId}/void`,
    request
  )
}
