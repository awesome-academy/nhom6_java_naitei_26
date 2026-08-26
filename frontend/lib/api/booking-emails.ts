import { apiClient } from "@/lib/api/client"
import type { BookingEmail, BookingEmailRequest } from "@/types/booking-email"

function bookingEmailsPath(bookingPublicId: string) {
  return `/api/bookings/${encodeURIComponent(bookingPublicId)}/emails`
}

export function getBookingEmails(bookingPublicId: string): Promise<BookingEmail[]> {
  return apiClient.get<BookingEmail[]>(bookingEmailsPath(bookingPublicId))
}

export function sendBookingEmail(
  bookingPublicId: string,
  request: BookingEmailRequest
): Promise<BookingEmail> {
  return apiClient.post<BookingEmail>(bookingEmailsPath(bookingPublicId), request)
}
