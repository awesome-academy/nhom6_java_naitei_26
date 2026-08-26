import { apiClient } from "@/lib/api/client"
import type {
  Review,
  ReviewCreateRequest,
  ReviewListResponse,
  ReviewModerationRequest,
  ReviewReplyRequest,
  ReviewStatus,
} from "@/types/review"

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

export interface AdminReviewFilters {
  status?: ReviewStatus
  roomTypeCode?: string
  rating?: number
  page?: number
  size?: number
}

export function getAdminReviews(filters: AdminReviewFilters = {}): Promise<ReviewListResponse> {
  const params = new URLSearchParams()
  if (filters.status) params.set("status", filters.status)
  if (filters.roomTypeCode) params.set("roomTypeCode", filters.roomTypeCode)
  if (filters.rating) params.set("rating", String(filters.rating))
  params.set("page", String(filters.page ?? 0))
  params.set("size", String(filters.size ?? 20))
  return apiClient.get<ReviewListResponse>(`/api/admin/reviews?${params.toString()}`)
}

export function moderateBookingReview(
  bookingPublicId: string,
  request: ReviewModerationRequest,
): Promise<Review> {
  return apiClient.post<Review>(`${reviewPath(bookingPublicId)}/moderate`, request)
}

export function replyToBookingReview(
  bookingPublicId: string,
  request: ReviewReplyRequest,
): Promise<Review> {
  return apiClient.post<Review>(`${reviewPath(bookingPublicId)}/reply`, request)
}
