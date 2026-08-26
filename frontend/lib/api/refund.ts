import { apiClient } from "@/lib/api/client"
import type { RefundCompleteRequest, RefundPreviewResponse, RefundResponse } from "@/types/refund"

export const requestRefund = (bookingPublicId: string) =>
  apiClient.post<RefundResponse>(`/api/bookings/${encodeURIComponent(bookingPublicId)}/refunds`, {})

export const getLatestRefund = (bookingPublicId: string) =>
  apiClient.get<RefundResponse>(`/api/bookings/${encodeURIComponent(bookingPublicId)}/refunds`)

export const previewRefund = (bookingPublicId: string) =>
  apiClient.get<RefundPreviewResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/refunds/preview`
  )

export const approveRefund = (bookingPublicId: string, refundId: number) =>
  apiClient.post<RefundResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/refunds/${refundId}/approve`,
    {}
  )

export const completeRefund = (
  bookingPublicId: string,
  refundId: number,
  request: RefundCompleteRequest = {}
) =>
  apiClient.post<RefundResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/refunds/${refundId}/complete`,
    request
  )
