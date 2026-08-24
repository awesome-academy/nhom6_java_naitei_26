import { apiClient } from "@/lib/api/client";
import type {
  Booking,
  BookingCreateRequest,
  PriceCalculation,
} from "@/types/booking";
import type { Room } from "@/types/room";
import type { RoomType } from "@/types/room-type";

export const getBookingRoomTypes = () =>
  apiClient.get<RoomType[]>("/api/room-types");
export const getBookingRooms = () => apiClient.get<Room[]>("/api/rooms");
export const getMyBookings = () => apiClient.get<Booking[]>("/api/bookings/me");
export const deletePendingBooking = (bookingPublicId: string) =>
  apiClient.delete<void>(`/api/bookings/${bookingPublicId}`);
export const deletePendingBookingRoom = (
  bookingPublicId: string,
  bookingRoomId: number,
) => apiClient.delete<Booking | void>(`/api/bookings/${bookingPublicId}/rooms/${bookingRoomId}`);
export const getAvailability = (checkInDate: string, checkOutDate: string) =>
  apiClient.get<Record<string, number[]>>(
    `/api/rooms/availability?checkInDate=${checkInDate}&checkOutDate=${checkOutDate}`,
  );
export const calculateBookingPrice = (
  body: {
    roomTypeCode: string;
    paymentOption: "ONLINE" | "PAY_AT_HOTEL";
    cancellationPolicyCode: string;
    checkInDate: string;
    checkOutDate: string;
    adults: number;
    children: number;
  },
) => apiClient.post<PriceCalculation>("/api/bookings/calculate-price", body);
export const createBooking = (body: BookingCreateRequest) =>
  apiClient.post<Booking>("/api/bookings", body);
export const addBookingGuest = (
  bookingId: string,
  body: { bookingRoomId: number; fullName: string; nationality?: string },
) => apiClient.post(`/api/bookings/${bookingId}/guests`, body);
