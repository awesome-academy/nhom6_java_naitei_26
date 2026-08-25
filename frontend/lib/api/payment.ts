import { apiClient } from "@/lib/api/client"
import type {
  MockWalletResult,
  PaymentMethod,
  PaymentResponse,
  PaymentStatusResponse,
} from "@/types/payment"

export const createPayment = (
  bookingPublicId: string,
  method: PaymentMethod,
  idempotencyKey: string
) =>
  apiClient.post<PaymentResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/payments`,
    { method },
    { "Idempotency-Key": idempotencyKey }
  )

export const getPayment = (bookingPublicId: string, paymentCode: string) =>
  apiClient.get<PaymentStatusResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/payments/${encodeURIComponent(paymentCode)}`
  )

export const cancelPayment = (bookingPublicId: string, paymentCode: string) =>
  apiClient.post<PaymentStatusResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/payments/${encodeURIComponent(paymentCode)}/cancel`,
    {}
  )

export const submitMockWalletResult = (
  bookingPublicId: string,
  paymentCode: string,
  result: MockWalletResult
) =>
  apiClient.post<PaymentStatusResponse>(
    `/api/bookings/${encodeURIComponent(bookingPublicId)}/payments/${encodeURIComponent(paymentCode)}/mock-wallet/result`,
    { result }
  )
