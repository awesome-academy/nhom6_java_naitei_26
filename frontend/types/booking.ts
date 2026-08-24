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

export interface DailyRate {
  date: string;
  price: number;
  rateOverrideId?: number | null;
}
export interface PriceCalculation {
  roomId: number;
  roomTypeId: number;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  adults: number;
  children: number;
  dailyRates: DailyRate[];
  roomsTotal: number;
  roomTaxPercentSnapshot: number;
  taxTotal: number;
  totalAmount: number;
  currency: string;
}
export interface CancellationRule {
  minHoursBefore: number;
  refundPercent: number;
}
export interface CancellationPolicy {
  code: string;
  name: string;
  description: string | null;
  noShowChargePercent: number;
  isDefault: boolean;
  isActive: boolean;
  rules: CancellationRule[];
}
export interface BookingRoomItem {
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  adults: number;
  children: number;
}
export interface BookingCreateRequest {
  cancellationPolicyCode: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  specialRequests?: string;
  rooms: BookingRoomItem[];
}
export interface BookingRoom {
  bookingRoomId: number;
  roomNumber: string;
  roomTypeCode: string;
  roomTypeName: string;
  checkInDate: string;
  checkOutDate: string;
  status: BookingRoomStatus;
  guestCount: number;
  roomSubtotal: number;
  nights: { stayDate: string; price: number }[];
}
export interface Booking {
  publicId: string;
  bookingCode: string;
  status: BookingStatus;
  paymentStatus: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string | null;
  adults: number;
  children: number;
  roomsTotal: number;
  taxTotal: number;
  totalAmount: number;
  currency: string;
  cancellationPolicyCode: string;
  holdExpiresAt: string;
  rooms: BookingRoom[];
  createdAt: string;
}
