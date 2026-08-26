export type ReviewStatus = "PENDING" | "PUBLISHED" | "HIDDEN" | "REJECTED"

export interface Review {
  id: number
  bookingPublicId: string
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
  staffReply: string | null
  staffReplyBy: number | null
  staffRepliedAt: string | null
  createdAt: string
  updatedAt: string
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
