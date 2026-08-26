import { apiClient } from "./client"
import type {
  PaymentDetail,
  PaymentListResponse,
  PaymentMethod,
  PaymentStatus,
  RefundReason,
} from "@/types/payment-management"

const ENDPOINT = "/api/admin/payments"

export interface PaymentManagementFilters {
  booking?: string
  status?: PaymentStatus
  method?: PaymentMethod
  from?: string
  to?: string
  page?: number
  size?: number
}

export async function getManagedPayments(
  filters: PaymentManagementFilters = {}
): Promise<PaymentListResponse> {
  const params = new URLSearchParams()
  if (filters.booking) params.set("booking", filters.booking)
  if (filters.status) params.set("status", filters.status)
  if (filters.method) params.set("method", filters.method)
  if (filters.from) params.set("from", filters.from)
  if (filters.to) params.set("to", filters.to)
  if (filters.page !== undefined) params.set("page", String(filters.page))
  if (filters.size !== undefined) params.set("size", String(filters.size))

  const query = params.toString()
  return apiClient.get<PaymentListResponse>(`${ENDPOINT}${query ? `?${query}` : ""}`)
}

export function getManagedPayment(paymentCode: string): Promise<PaymentDetail> {
  return apiClient.get<PaymentDetail>(`${ENDPOINT}/${encodeURIComponent(paymentCode)}`)
}

export function verifyCashPayment(
  paymentCode: string,
  providerTxnId?: string
): Promise<PaymentDetail> {
  return apiClient.post<PaymentDetail>(
    `${ENDPOINT}/${encodeURIComponent(paymentCode)}/verify-cash`,
    providerTxnId?.trim() ? { providerTxnId: providerTxnId.trim() } : {}
  )
}

export function requestPaymentRefund(
  paymentCode: string,
  request: { amount: number; reason: RefundReason }
): Promise<PaymentDetail> {
  return apiClient.post<PaymentDetail>(
    `${ENDPOINT}/${encodeURIComponent(paymentCode)}/refunds`,
    request
  )
}
