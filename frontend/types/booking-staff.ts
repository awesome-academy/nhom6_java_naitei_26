// Staff-facing booking types

import type { InvoiceResponse } from "@/types/invoice";
import type { PaymentMethod, PaymentStatus as GatewayPaymentStatus } from "@/types/payment";

export type BookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CHECKED_OUT"
  | "CANCELLED"
  | "NO_SHOW"
  | "EXPIRED";

export type BookingRoomStatus =
  | "RESERVED"
  | "OCCUPIED"
  | "COMPLETED"
  | "RELEASED"
  | "MOVED_OUT";

export type PaymentStatus = GatewayPaymentStatus;

export type BookingPaymentStatus =
  | "UNPAID"
  | "PARTIALLY_PAID"
  | "PAID"
  | "REFUNDED";

// Filter request
export interface BookingListFilterRequest {
  status?: BookingStatus[];
  checkInFrom?: string;
  checkInTo?: string;
  checkOutFrom?: string;
  checkOutTo?: string;
  source?: string;
  search?: string;
  page?: number;
  size?: number;
}

// Stats
export interface BookingStats {
  total: number;
  pending: number;
  confirmed: number;
  checkedIn: number;
  checkedOut: number;
  cancelled: number;
}

// Room summary in list
export interface BookingRoomSummary {
  id: number;
  roomNumber: string | null;
  roomTypeCode: string;
  roomTypeName: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  roomSubtotal: number;
  status: BookingRoomStatus;
}

// Dates summary
export interface BookingDatesSummary {
  earliestCheckIn: string;
  latestCheckOut: string;
  totalNights: number;
}

// Booking list item
export interface BookingListItem {
  publicId: string;
  bookingCode: string;
  status: BookingStatus;
  paymentStatus: BookingPaymentStatus;
  sourceCode: string;
  sourceName: string;
  contactName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  adults: number;
  children: number;
  totalAmount: number;
  currency: string;
  holdExpiresAt: string | null;
  createdAt: string;
  rooms: BookingRoomSummary[];
  dates: BookingDatesSummary;
  allRoomsAssigned: boolean;
}

// Paginated list response
export interface BookingListResponse {
  items: BookingListItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  stats: BookingStats;
}

// Staff booking detail
export interface BookingRoomNightResponse {
  stayDate: string;
  price: number;
}

export interface BookingRoomDetail {
  id: number;
  roomId: number | null;
  roomNumber: string | null;
  roomTypeCode: string;
  roomTypeName: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  roomSubtotal: number;
  bookingRoomStatus: BookingRoomStatus;
  guestCount: number;
  cancellationPolicyCode: string | null;
  cancellationPolicyName: string | null;
  cancellationPolicySnapshot: string | null;
  paymentOption: string;
  priceAdjustmentPercentSnapshot: number;
  assignedAt: string | null;
  assignedByName: string | null;
  nightlyRates: BookingRoomNightResponse[];
}

export interface BookingGuestResponse {
  id: number;
  bookingRoomId: number | null;
  roomNumber: string | null;
  fullName: string;
  nationality: string | null;
  idDocumentType: string | null;
  hasIdDocument: boolean;
  dateOfBirth: string | null;
  createdAt: string;
}

export type BookingCheckInGuest = {
  fullName: string;
  nationality?: string;
  idDocumentType: "NATIONAL_ID" | "PASSPORT" | "DRIVER_LICENSE";
  idDocumentNumber: string;
  dateOfBirth?: string;
};

export interface BookingCheckInRoom {
  bookingRoomId: number;
  guestCount: number;
  guests: BookingCheckInGuest[];
}

export interface BookingCheckInRequest {
  rooms: BookingCheckInRoom[];
}

export interface FolioChargeResponse {
  id: number;
  bookingPublicId: string;
  serviceItemCode: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineSubtotal: number;
  discountAmount: number;
  taxPercent: number;
  taxAmount: number;
  lineTotal: number;
  chargedAt: string;
  chargedBy: number | null;
  isVoided: boolean;
  voidedAt: string | null;
  voidedBy: number | null;
  voidReason: string | null;
}

export interface PaymentResponse {
  paymentCode: string;
  bookingPublicId: string;
  method: PaymentMethod | "CASH" | "BANK_TRANSFER";
  amount: number;
  currency: string;
  status: PaymentStatus;
  provider: string | null;
  paymentUrl: string | null;
  deeplink: string | null;
  qrCodeValue: string | null;
  checkoutFields: { name: string; value: string }[];
  expiresAt: string | null;
  createdAt: string;
}

export interface StatusHistoryResponse {
  fromStatus: BookingStatus | null;
  toStatus: BookingStatus;
  actorType: string;
  source: string;
  reason: string | null;
  createdAt: string | null;
}

export interface BookingStaffDetail {
  publicId: string;
  bookingCode: string;
  status: BookingStatus;
  paymentStatus: BookingPaymentStatus;
  sourceCode: string;
  sourceName: string;
  sourceCommissionPercentSnapshot: string | null;
  contactName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  contactAddress: string | null;
  adults: number;
  children: number;
  roomsTotal: number;
  servicesTotal: number;
  discountTotal: number;
  taxTotal: number;
  totalAmount: number;
  currency: string;
  paidAmount: number;
  refundedAmount: number;
  specialRequests: string | null;
  internalNotes: string | null;
  holdExpiresAt: string | null;
  confirmedAt: string | null;
  confirmedByName: string | null;
  checkedInAt: string | null;
  checkedInByName: string | null;
  checkedOutAt: string | null;
  checkedOutByName: string | null;
  cancelledAt: string | null;
  cancelledBy: number | null;
  cancellationReason: string | null;
  createdAt: string;
  customerId: number | null;
  customerName: string | null;
  customerEmail: string | null;
  customerPhone: string | null;
  customerLoyaltyPoints: number | null;
  rooms: BookingRoomDetail[];
  guests: BookingGuestResponse[];
  folioCharges: FolioChargeResponse[];
  payments: PaymentResponse[];
  invoices: InvoiceResponse[];
  statusHistory: StatusHistoryResponse[];
}

// Room assignment
export interface RoomAssignmentRequest {
  roomId: number;
}

export interface BookingConfirmResponse {
  publicId: string;
  bookingCode: string;
  status: BookingStatus;
  confirmedAt: string;
  checkedInAt: string | null;
}

// Room for assignment modal
export interface HousekeepingStatus {
  CLEAN: "CLEAN";
  DIRTY: "DIRTY";
  CLEANING: "CLEANING";
}

export type HousekeepingStatusType = "CLEAN" | "DIRTY" | "CLEANING";

export type RoomView = "SEA" | "CITY" | "GARDEN" | "POOL" | "MOUNTAIN" | "NONE";

export interface AvailableRoom {
  id: number;
  roomNumber: string;
  roomTypeCode: string;
  roomTypeName: string;
  floor: number | null;
  viewType: RoomView;
  housekeepingStatus: HousekeepingStatusType;
  operationalStatus: string;
  priceOverride: number | null;
  effectivePrice: number | null;
}

// Cancel request
export interface CancelBookingRequest {
  reason: string;
}
