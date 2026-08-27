import type { RefundReason, RefundStatus } from "@/types/refund"

export type { RefundReason, RefundStatus }

export type PaymentMethod =
  | "INTERNET_BANKING"
  | "CARD"
  | "CASH"
  | "BANK_TRANSFER"
  | "E_WALLET"

export type PaymentStatus =
  | "PENDING"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED"
  | "REFUNDED"
  | "PARTIALLY_REFUNDED"

export interface PaymentListItem {
  paymentCode: string
  bookingPublicId: string
  bookingCode: string
  contactName: string
  method: PaymentMethod
  amount: number
  currency: string
  status: PaymentStatus
  provider: string | null
  providerTxnId: string | null
  refundedAmount: number
  paidAt: string | null
  verifiedAt: string | null
  createdAt: string | null
}

export interface PaymentListResponse {
  items: PaymentListItem[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface PaymentRefundSummary {
  id: number
  amount: number
  reason: RefundReason
  status: RefundStatus
  requestedBy: number | null
  approvedBy: number | null
  providerRefundId: string | null
  createdAt: string | null
  processedAt: string | null
}

export interface PaymentDetail extends PaymentListItem {
  bookingStatus: string
  bookingPaymentStatus: string
  bookingTotalAmount: number
  bookingPaidAmount: number
  bookingRefundedAmount: number
  providerBankCode: string | null
  expiresAt: string | null
  createdBy: number | null
  updatedAt: string | null
  refunds: PaymentRefundSummary[]
}
