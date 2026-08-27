export type ReviewStatus = "PENDING" | "PUBLISHED" | "HIDDEN" | "REJECTED"

export interface Review {
  id: number
  bookingPublicId: string
  bookingCode: string | null
  customerName: string | null
  customerEmail: string | null
  roomNumber: string | null
  roomTypeCode: string | null
  roomTypeName: string | null
  overallRating: number
  roomRating: number | null
  cleanlinessRating: number | null
  serviceRating: number | null
  valueRating: number | null
  title: string | null
  comment: string | null
  status: ReviewStatus
  moderationReason: string | null
  staffReply: string | null
  staffReplyBy: number | null
  staffRepliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ReviewListResponse {
  items: Review[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface PublishedReview {
  customerName: string | null
  roomTypeName: string | null
  overallRating: number
  roomRating: number | null
  cleanlinessRating: number | null
  serviceRating: number | null
  valueRating: number | null
  title: string | null
  comment: string | null
  staffReply: string | null
  staffRepliedAt: string | null
  createdAt: string
}

export interface PublishedReviewSummary {
  totalReviews: number
  averageOverallRating: number | null
  averageRoomRating: number | null
  averageCleanlinessRating: number | null
  averageServiceRating: number | null
  averageValueRating: number | null
}

export interface PublishedReviewListResponse {
  items: PublishedReview[]
  summary: PublishedReviewSummary
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface ReviewModerationRequest {
  status: Exclude<ReviewStatus, "PENDING">
  moderationReason?: string
}

export interface ReviewReplyRequest {
  staffReply: string
}

export interface ReviewCreateRequest {
  overallRating: number
  roomRating?: number
  cleanlinessRating?: number
  serviceRating?: number
  valueRating?: number
  title?: string
  comment?: string
}
