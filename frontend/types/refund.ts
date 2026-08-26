export type RefundStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "REJECTED"

export type RefundReason = "CUSTOMER_CANCEL" | "HOTEL_CANCEL" | "OVERCHARGE" | "NO_SHOW_ADJUST" | "OTHER"

export interface RefundResponse {
  id: number
  bookingPublicId: string
  paymentCode: string
  amount: number
  reason: RefundReason
  status: RefundStatus
  policyApplied: string
  requestedBy: number | null
  approvedBy: number | null
  providerRefundId: string | null
  processedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface RefundPreviewResponse {
  bookingPublicId: string
  currency: string
  asOf: string
  hasReceivedPayment: boolean
  estimatedNetRefund: number
  policyApplied: string
}

export interface RefundCompleteRequest {
  providerRefundId?: string | null
}
