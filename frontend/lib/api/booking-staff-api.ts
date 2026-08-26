import { apiClient } from "./client";
import type { Booking } from "@/types/booking";
import type { PaymentMethod, PaymentResponse, PaymentStatusResponse } from "@/types/payment";
import type {
  RoomBookingMapRoom,
} from "@/types/room-booking-map";
import {
  BookingListFilterRequest,
  BookingListResponse,
  BookingStaffDetail,
  AvailableRoom,
  RoomAssignmentRequest,
  BookingConfirmResponse,
  BookingCheckInRequest,
  CancelBookingRequest,
  HousekeepingStatusType,
  RoomView,
} from "@/types/booking-staff";

const STAFF_BOOKINGS_ENDPOINT = "/api/admin/bookings";

export interface StaffBookingRoomCreateItem {
  roomNumber: string;
  roomTypeCode: string;
  paymentOption: "ONLINE" | "PAY_AT_HOTEL";
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  guests: StaffBookingGuestCreateItem[];
}

export type StaffBookingIdDocumentType = "NATIONAL_ID" | "PASSPORT" | "DRIVER_LICENSE";

export interface StaffBookingGuestCreateItem {
  fullName: string;
  nationality?: string;
  idDocumentType: StaffBookingIdDocumentType;
  idDocumentNumber: string;
  dateOfBirth?: string;
}

export interface StaffBookingCreateRequest {
  contactName: string;
  contactEmail?: string;
  contactPhone: string;
  specialRequests?: string;
  rooms: StaffBookingRoomCreateItem[];
}

export function getStaffRoomBookingMap(
  checkInDate: string,
  checkOutDate: string
): Promise<RoomBookingMapRoom[]> {
  const params = new URLSearchParams({ checkInDate, checkOutDate });
  return apiClient.get<RoomBookingMapRoom[]>(
    `/api/admin/rooms/booking-map?${params.toString()}`
  );
}

export function createStaffBooking(
  request: StaffBookingCreateRequest
): Promise<Booking> {
  return apiClient.post<Booking>(STAFF_BOOKINGS_ENDPOINT, request);
}

export function createStaffPayment(
  bookingPublicId: string,
  method: PaymentMethod,
  idempotencyKey: string
): Promise<PaymentResponse> {
  return apiClient.post<PaymentResponse>(
    `${STAFF_BOOKINGS_ENDPOINT}/${encodeURIComponent(bookingPublicId)}/payments`,
    { method },
    { "Idempotency-Key": idempotencyKey }
  );
}

export function getStaffPayment(
  bookingPublicId: string,
  paymentCode: string
): Promise<PaymentStatusResponse> {
  return apiClient.get<PaymentStatusResponse>(
    `${STAFF_BOOKINGS_ENDPOINT}/${encodeURIComponent(bookingPublicId)}/payments/${encodeURIComponent(paymentCode)}`
  );
}

export function cancelStaffPayment(
  bookingPublicId: string,
  paymentCode: string
): Promise<PaymentStatusResponse> {
  return apiClient.post<PaymentStatusResponse>(
    `${STAFF_BOOKINGS_ENDPOINT}/${encodeURIComponent(bookingPublicId)}/payments/${encodeURIComponent(paymentCode)}/cancel`,
    {}
  );
}

export function submitStaffMockWalletResult(
  bookingPublicId: string,
  paymentCode: string,
  result: "SUCCEEDED" | "FAILED"
): Promise<PaymentStatusResponse> {
  return apiClient.post<PaymentStatusResponse>(
    `${STAFF_BOOKINGS_ENDPOINT}/${encodeURIComponent(bookingPublicId)}/payments/${encodeURIComponent(paymentCode)}/mock-wallet/result`,
    { result }
  );
}

/**
 * Get paginated list of bookings with filters
 */
export async function getBookings(
  filters: BookingListFilterRequest = {}
): Promise<BookingListResponse> {
  const params = new URLSearchParams();

  if (filters.status?.length) {
    filters.status.forEach((s) => params.append("status", s));
  }
  if (filters.checkInFrom) params.append("checkInFrom", filters.checkInFrom);
  if (filters.checkInTo) params.append("checkInTo", filters.checkInTo);
  if (filters.checkOutFrom) params.append("checkOutFrom", filters.checkOutFrom);
  if (filters.checkOutTo) params.append("checkOutTo", filters.checkOutTo);
  if (filters.source) params.append("source", filters.source);
  if (filters.search) params.append("search", filters.search);
  if (filters.page !== undefined) params.append("page", String(filters.page));
  if (filters.size !== undefined) params.append("size", String(filters.size));

  const queryString = params.toString();
  const endpoint = `${STAFF_BOOKINGS_ENDPOINT}${queryString ? `?${queryString}` : ""}`;

  return apiClient.get<BookingListResponse>(endpoint);
}

/**
 * Get booking detail for staff view
 */
export async function getBookingDetail(publicId: string): Promise<BookingStaffDetail> {
  return apiClient.get<BookingStaffDetail>(`${STAFF_BOOKINGS_ENDPOINT}/${publicId}`);
}

/**
 * Confirm a pending booking
 */
export async function confirmBooking(publicId: string): Promise<BookingConfirmResponse> {
  return apiClient.post<BookingConfirmResponse>(`${STAFF_BOOKINGS_ENDPOINT}/${publicId}/confirm`, {});
}

/**
 * Get available rooms for assignment to a booking room
 */
export async function getAvailableRoomsForAssignment(
  bookingPublicId: string,
  bookingRoomId: number,
  filters?: {
    floor?: number;
    housekeepingStatus?: HousekeepingStatusType;
    viewType?: RoomView;
  }
): Promise<AvailableRoom[]> {
  const params = new URLSearchParams();

  if (filters?.floor !== undefined) params.append("floor", String(filters.floor));
  if (filters?.housekeepingStatus)
    params.append("housekeepingStatus", filters.housekeepingStatus);
  if (filters?.viewType) params.append("viewType", filters.viewType);

  const queryString = params.toString();
  const endpoint = `${STAFF_BOOKINGS_ENDPOINT}/${bookingPublicId}/rooms/${bookingRoomId}/available-rooms${queryString ? `?${queryString}` : ""}`;

  return apiClient.get<AvailableRoom[]>(endpoint);
}

/**
 * Get available floors for room assignment
 */
export async function getAvailableFloors(
  bookingPublicId: string,
  bookingRoomId: number
): Promise<number[]> {
  return apiClient.get<number[]>(
    `${STAFF_BOOKINGS_ENDPOINT}/${bookingPublicId}/rooms/${bookingRoomId}/available-floors`
  );
}

/**
 * Assign a room to a booking room
 */
export async function assignRoom(
  bookingPublicId: string,
  bookingRoomId: number,
  request: RoomAssignmentRequest
): Promise<void> {
  return apiClient.post<void>(
    `${STAFF_BOOKINGS_ENDPOINT}/${bookingPublicId}/rooms/${bookingRoomId}/assign`,
    request
  );
}

/**
 * Check-in a booking
 */
export async function checkInBooking(
  publicId: string,
  request: BookingCheckInRequest = { rooms: [] },
): Promise<Booking> {
  return apiClient.post<Booking>(`${STAFF_BOOKINGS_ENDPOINT}/${publicId}/check-in`, request);
}

/**
 * Check-out a booking
 */
export async function checkOutBooking(publicId: string): Promise<unknown> {
  return apiClient.post<unknown>(`${STAFF_BOOKINGS_ENDPOINT}/${publicId}/check-out`, {});
}

/**
 * Cancel a booking
 */
export async function cancelBooking(
  publicId: string,
  request: CancelBookingRequest
): Promise<unknown> {
  return apiClient.post<unknown>(`${STAFF_BOOKINGS_ENDPOINT}/${publicId}/cancel`, request);
}
