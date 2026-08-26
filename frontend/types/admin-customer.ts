export type CustomerAccountStatus = "ACTIVE" | "DEACTIVATED"
export type CustomerStatus = CustomerAccountStatus | "PENDING_VERIFICATION" | "SUSPENDED"

export interface CustomerListItem {
  publicId: string
  fullName: string
  email: string
  phone: string | null
  status: CustomerStatus
  bookingCount: number
  createdAt: string
}

export interface CustomerListResponse {
  items: CustomerListItem[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface CustomerAccount {
  publicId: string
  email: string
  emailVerifiedAt: string | null
  phone: string | null
  fullName: string
  avatarUrl: string | null
  status: CustomerStatus
  roles: string[]
  createdAt: string
  updatedAt: string
}

export interface CustomerProfile {
  publicId: string
  email: string
  phone: string | null
  fullName: string
  dateOfBirth: string | null
  gender: string | null
  nationality: string | null
  province: string | null
  addressLine: string | null
  country: string | null
  avatarUrl: string | null
  emailVerified: boolean
  joinedAt: string
  loyaltyPoints: number
  totalStays: number
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface CustomerDetailResponse {
  account: CustomerAccount
  profile: CustomerProfile | null
}

export type CustomerBookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CHECKED_OUT"
  | "CANCELLED"
  | "NO_SHOW"
  | "EXPIRED"

export type CustomerPaymentStatus =
  | "UNPAID"
  | "PARTIALLY_PAID"
  | "PAID"
  | "PARTIALLY_REFUNDED"
  | "REFUNDED"

export interface CustomerBooking {
  bookingCode: string
  checkInDate: string | null
  checkOutDate: string | null
  nights: number
  rooms: number
  guests: number
  totalAmount: number | string
  currency: string
  status: CustomerBookingStatus
  paymentStatus: CustomerPaymentStatus
}
