import type { Booking } from "@/types/booking"
import type { PaymentMethod, PaymentResponse } from "@/types/payment"

const ACTIVE_CHECKOUT_KEY = "hotel-payment-active-checkout"
const IDEMPOTENCY_PREFIX = "hotel-payment-idempotency"

export interface PaymentCheckoutSession {
  bookingPublicId: string
  bookingCode: string
  paymentCode: string
  method: PaymentMethod
  amount: number
  currency: string
  provider: string
  paymentUrl: string | null
  qrCodeValue: string | null
  expiresAt: string | null
}

type CheckoutBooking = Pick<Booking, "publicId" | "bookingCode">

export function savePaymentCheckout(booking: CheckoutBooking, payment: PaymentResponse) {
  const checkout: PaymentCheckoutSession = {
    bookingPublicId: booking.publicId,
    bookingCode: booking.bookingCode,
    paymentCode: payment.paymentCode,
    method: payment.method,
    amount: Number(payment.amount),
    currency: payment.currency,
    provider: payment.provider,
    paymentUrl: payment.paymentUrl,
    qrCodeValue: payment.qrCodeValue,
    expiresAt: payment.expiresAt,
  }
  window.sessionStorage.setItem(ACTIVE_CHECKOUT_KEY, JSON.stringify(checkout))
}

export function loadPaymentCheckout(): PaymentCheckoutSession | null {
  const rawCheckout = window.sessionStorage.getItem(ACTIVE_CHECKOUT_KEY)
  if (!rawCheckout) return null

  try {
    const checkout = JSON.parse(rawCheckout) as Partial<PaymentCheckoutSession>
    if (
      typeof checkout.bookingPublicId !== "string" ||
      typeof checkout.bookingCode !== "string" ||
      typeof checkout.paymentCode !== "string" ||
      !isPaymentMethod(checkout.method) ||
      typeof checkout.amount !== "number" ||
      typeof checkout.currency !== "string" ||
      typeof checkout.provider !== "string"
    ) {
      return null
    }
    return checkout as PaymentCheckoutSession
  } catch {
    return null
  }
}

function isPaymentMethod(value: unknown): value is PaymentMethod {
  return value === "INTERNET_BANKING" || value === "CARD" || value === "E_WALLET"
}

export function clearPaymentCheckout() {
  window.sessionStorage.removeItem(ACTIVE_CHECKOUT_KEY)
}

export function getOrCreateIdempotencyKey(
  bookingPublicId: string,
  method: PaymentMethod
) {
  const storageKey = `${IDEMPOTENCY_PREFIX}:${bookingPublicId}:${method}`
  const existingKey = window.sessionStorage.getItem(storageKey)
  if (existingKey) return existingKey

  const newKey = crypto.randomUUID()
  window.sessionStorage.setItem(storageKey, newKey)
  return newKey
}

export function clearPaymentIdempotencyKey(
  bookingPublicId: string,
  method: PaymentMethod
) {
  window.sessionStorage.removeItem(
    `${IDEMPOTENCY_PREFIX}:${bookingPublicId}:${method}`
  )
}

export function redirectToPaymentCheckout(
  booking: CheckoutBooking,
  payment: PaymentResponse,
  options?: { staffBooking?: boolean }
) {
  if (!payment.paymentUrl) {
    throw new Error("Cổng thanh toán không trả về địa chỉ checkout.")
  }

  const checkoutUrl = new URL(payment.paymentUrl, window.location.origin)
  if (checkoutUrl.protocol !== "http:" && checkoutUrl.protocol !== "https:") {
    throw new Error("Địa chỉ cổng thanh toán không hợp lệ.")
  }

  savePaymentCheckout(booking, payment)

  if (options?.staffBooking && payment.provider === "MOCK_WALLET") {
    window.location.assign(
      `/manager/bookings/payment/${encodeURIComponent(payment.paymentCode)}?bookingId=${encodeURIComponent(booking.publicId)}`
    )
    return
  }

  if (payment.checkoutFields.length > 0) {
    const form = document.createElement("form")
    form.method = "POST"
    form.action = checkoutUrl.toString()

    payment.checkoutFields.forEach((field) => {
      const input = document.createElement("input")
      input.type = "hidden"
      input.name = field.name
      input.value = field.value
      form.appendChild(input)
    })

    document.body.appendChild(form)
    form.submit()
    return
  }

  checkoutUrl.searchParams.set("bookingId", booking.publicId)
  checkoutUrl.searchParams.set("paymentCode", payment.paymentCode)
  window.location.assign(checkoutUrl.toString())
}
