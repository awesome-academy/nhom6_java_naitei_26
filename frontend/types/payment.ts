export type PaymentMethod = "INTERNET_BANKING" | "CARD" | "E_WALLET"

export type PaymentStatus =
  | "PENDING"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED"
  | "REFUNDED"
  | "PARTIALLY_REFUNDED"

export interface PaymentGatewayFormField {
  name: string
  value: string
}

export interface PaymentResponse {
  paymentCode: string
  bookingPublicId: string
  method: PaymentMethod
  amount: number
  currency: string
  status: PaymentStatus
  provider: string
  paymentUrl: string | null
  deeplink: string | null
  qrCodeValue: string | null
  checkoutFields: PaymentGatewayFormField[]
  expiresAt: string | null
  createdAt: string
}

export interface PaymentStatusResponse {
  paymentCode: string
  bookingPublicId: string
  method: PaymentMethod
  amount: number
  currency: string
  status: PaymentStatus
  provider: string
  failureCode: string | null
  failureMessage: string | null
  expiresAt: string | null
  retryable: boolean
  createdAt: string
  updatedAt: string
}

export type MockWalletResult = "SUCCEEDED" | "FAILED"
