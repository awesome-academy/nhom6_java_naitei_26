import { apiClient } from "@/lib/api/client"
import type { Review, ReviewCreateRequest } from "@/types/review"

function reviewPath(bookingPublicId: string) {
  return `/api/bookings/${encodeURIComponent(bookingPublicId)}/review`
}

export function getBookingReview(bookingPublicId: string) {
  return apiClient.get<Review>(reviewPath(bookingPublicId))
}

export function createBookingReview(
  bookingPublicId: string,
  request: ReviewCreateRequest,
) {
  return apiClient.post<Review>(reviewPath(bookingPublicId), request)
}
