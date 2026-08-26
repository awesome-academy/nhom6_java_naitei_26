"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { ArrowLeft, CheckCircle2, Star } from "lucide-react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { getMyBookingDetail } from "@/lib/api/booking"
import { createBookingReview, getBookingReview } from "@/lib/api/reviews"
import type { BookingDetail } from "@/types/booking"
import type { Review } from "@/types/review"

type RatingKey = "overallRating" | "roomRating" | "cleanlinessRating" | "serviceRating" | "valueRating"

type Ratings = Record<RatingKey, number | null>

const ratingFields: { key: RatingKey; label: string; required?: boolean }[] = [
  { key: "overallRating", label: "Đánh giá tổng thể", required: true },
  { key: "roomRating", label: "Phòng" },
  { key: "cleanlinessRating", label: "Vệ sinh" },
  { key: "serviceRating", label: "Dịch vụ" },
  { key: "valueRating", label: "Giá trị nhận được" },
]

const reviewStatusLabels: Record<Review["status"], string> = {
  PENDING: "Đang chờ duyệt",
  PUBLISHED: "Đã đăng",
  HIDDEN: "Đã ẩn",
  REJECTED: "Đã từ chối",
}

const initialRatings: Ratings = {
  overallRating: null,
  roomRating: null,
  cleanlinessRating: null,
  serviceRating: null,
  valueRating: null,
}

function getApiError(error: unknown) {
  const apiError = error as Error & { status?: number }
  return apiError.message || "Không thể thực hiện thao tác. Vui lòng thử lại."
}

function isNotFound(error: unknown) {
  return (error as Error & { status?: number }).status === 404
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(value))
}

function RatingInput({
  label,
  value,
  required,
  onChange,
}: {
  label: string
  value: number | null
  required?: boolean
  onChange: (value: number) => void
}) {
  return (
    <div className="flex flex-col gap-2">
      <Label>
        {label}
        {required && <span className="text-destructive"> *</span>}
      </Label>
      <div className="flex items-center gap-1" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((rating) => (
          <Button
            key={rating}
            type="button"
            variant="ghost"
            size="icon"
            className="rounded-full"
            aria-label={`${rating} sao`}
            aria-pressed={value === rating}
            onClick={() => onChange(rating)}
          >
            <Star className={value !== null && rating <= value ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--muted-foreground)]"} />
          </Button>
        ))}
        <span className="ml-2 text-sm text-muted-foreground">
          {value === null ? "Chưa chọn" : `${value}/5`}
        </span>
      </div>
    </div>
  )
}

function ReviewSummary({ review }: { review: Review }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-col gap-2">
            <CardTitle>Đánh giá của bạn</CardTitle>
            <CardDescription>
              Đã gửi ngày {formatDate(review.createdAt)}
            </CardDescription>
          </div>
          <Badge variant="secondary">{reviewStatusLabels[review.status]}</Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex items-center gap-2 text-sm">
          <span className="font-medium">Tổng thể</span>
          <span className="flex items-center gap-1">
            <Star className="fill-[var(--accent)] text-[var(--accent)]" />
            {review.overallRating}/5
          </span>
        </div>
        {review.title && <p className="font-semibold">{review.title}</p>}
        {review.comment && <p className="whitespace-pre-wrap text-sm text-muted-foreground">{review.comment}</p>}
        {review.staffReply && (
          <Alert>
            <AlertTitle>Phản hồi từ khách sạn</AlertTitle>
            <AlertDescription>{review.staffReply}</AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  )
}

export function ReviewSubmissionPage({ bookingPublicId }: { bookingPublicId: string }) {
  const [detail, setDetail] = useState<BookingDetail | null>(null)
  const [existingReview, setExistingReview] = useState<Review | null>(null)
  const [ratings, setRatings] = useState<Ratings>(initialRatings)
  const [title, setTitle] = useState("")
  const [comment, setComment] = useState("")
  const [isPreview, setIsPreview] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false

    async function loadReviewContext() {
      setIsLoading(true)
      setError(null)
      try {
        const bookingDetail = await getMyBookingDetail(bookingPublicId)
        if (ignore) return
        setDetail(bookingDetail)

        if (bookingDetail.booking.status === "CHECKED_OUT") {
          try {
            const review = await getBookingReview(bookingPublicId)
            if (!ignore) setExistingReview(review)
          } catch (reviewError) {
            if (!isNotFound(reviewError) && !ignore) {
              setError(getApiError(reviewError))
            }
          }
        }
      } catch (loadError) {
        if (!ignore) setError(getApiError(loadError))
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }

    loadReviewContext()
    return () => {
      ignore = true
    }
  }, [bookingPublicId])

  function updateRating(key: RatingKey, value: number) {
    setRatings((current) => ({ ...current, [key]: value }))
  }

  async function submitReview() {
    if (ratings.overallRating === null) {
      toast.error("Vui lòng chọn đánh giá tổng thể")
      return
    }

    setIsSubmitting(true)
    try {
      const review = await createBookingReview(bookingPublicId, {
        overallRating: ratings.overallRating,
        ...(ratings.roomRating === null ? {} : { roomRating: ratings.roomRating }),
        ...(ratings.cleanlinessRating === null ? {} : { cleanlinessRating: ratings.cleanlinessRating }),
        ...(ratings.serviceRating === null ? {} : { serviceRating: ratings.serviceRating }),
        ...(ratings.valueRating === null ? {} : { valueRating: ratings.valueRating }),
        ...(title.trim() ? { title: title.trim() } : {}),
        ...(comment.trim() ? { comment: comment.trim() } : {}),
      })
      setExistingReview(review)
      toast.success("Đã gửi đánh giá thành công")
    } catch (submitError) {
      if ((submitError as Error & { status?: number }).status === 409) {
        toast.error("Booking này đã được đánh giá")
        try {
          setExistingReview(await getBookingReview(bookingPublicId))
        } catch {
          // The original conflict is already shown to the customer.
        }
      } else {
        toast.error(getApiError(submitError))
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-5 w-96" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  if (error || !detail) {
    return (
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
        <Alert variant="destructive">
          <AlertTitle>Không thể mở trang đánh giá</AlertTitle>
          <AlertDescription>{error ?? "Không tìm thấy booking."}</AlertDescription>
        </Alert>
        <Button asChild variant="outline">
          <Link href="/profile/bookings">
            <ArrowLeft data-icon="inline-start" />
            Quay lại My Bookings
          </Link>
        </Button>
      </div>
    )
  }

  if (detail.booking.status !== "CHECKED_OUT") {
    return (
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
        <Alert>
          <AlertTitle>Chưa thể viết đánh giá</AlertTitle>
          <AlertDescription>
            Bạn chỉ có thể đánh giá sau khi booking hoàn tất lưu trú và checkout.
          </AlertDescription>
        </Alert>
        <Button asChild variant="outline">
          <Link href={`/profile/bookings/${encodeURIComponent(bookingPublicId)}`}>
            <ArrowLeft data-icon="inline-start" />
            Quay lại chi tiết booking
          </Link>
        </Button>
      </div>
    )
  }

  if (existingReview) {
    return (
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
        <div className="flex flex-col gap-2">
          <Button asChild variant="ghost" className="w-fit px-0">
            <Link href={`/profile/bookings/${encodeURIComponent(bookingPublicId)}`}>
              <ArrowLeft data-icon="inline-start" />
              Chi tiết booking
            </Link>
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">Đánh giá lưu trú</h1>
          <p className="text-muted-foreground">Booking {detail.booking.bookingCode}</p>
        </div>
        <Alert>
          <CheckCircle2 />
          <AlertTitle>Cảm ơn bạn đã đánh giá</AlertTitle>
          <AlertDescription>Đánh giá của bạn đã được ghi nhận.</AlertDescription>
        </Alert>
        <ReviewSummary review={existingReview} />
        <Button asChild variant="outline">
          <Link href="/profile/bookings">Về My Bookings</Link>
        </Button>
      </div>
    )
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Button asChild variant="ghost" className="w-fit px-0">
          <Link href={`/profile/bookings/${encodeURIComponent(bookingPublicId)}`}>
            <ArrowLeft data-icon="inline-start" />
            Chi tiết booking
          </Link>
        </Button>
        <h1 className="text-3xl font-bold tracking-tight">Viết đánh giá</h1>
        <p className="text-muted-foreground">
          Chia sẻ trải nghiệm của bạn về booking {detail.booking.bookingCode}.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Trải nghiệm của bạn</CardTitle>
          <CardDescription>Đánh giá tổng thể là bắt buộc. Các mục còn lại là tùy chọn.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          {ratingFields.map((field) => (
            <RatingInput
              key={field.key}
              label={field.label}
              required={field.required}
              value={ratings[field.key]}
              onChange={(value) => updateRating(field.key, value)}
            />
          ))}
          <div className="flex flex-col gap-2">
            <Label htmlFor="review-title">Tiêu đề</Label>
            <Input
              id="review-title"
              maxLength={200}
              placeholder="Tóm tắt trải nghiệm của bạn"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="review-comment">Nhận xét</Label>
            <Textarea
              id="review-comment"
              maxLength={10000}
              showCount
              placeholder="Điều gì khiến bạn hài lòng hoặc chưa hài lòng?"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
            />
          </div>
        </CardContent>
        <CardFooter className="flex flex-wrap justify-end gap-3">
          <Button type="button" variant="outline" onClick={() => setIsPreview((current) => !current)}>
            {isPreview ? "Chỉnh sửa" : "Xem preview"}
          </Button>
          <Button type="button" disabled={isSubmitting} onClick={submitReview}>
            {isSubmitting ? "Đang gửi..." : "Gửi đánh giá"}
          </Button>
        </CardFooter>
      </Card>

      {isPreview && (
        <Card>
          <CardHeader>
            <CardTitle>Preview đánh giá</CardTitle>
            <CardDescription>Nội dung hiển thị sau khi đánh giá được duyệt.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="flex items-center gap-2">
              <span className="font-medium">Tổng thể</span>
              <span className="flex items-center gap-1">
                <Star className={ratings.overallRating ? "fill-[var(--accent)] text-[var(--accent)]" : "text-[var(--muted-foreground)]"} />
                {ratings.overallRating ? `${ratings.overallRating}/5` : "Chưa chọn"}
              </span>
            </div>
            {title.trim() && <p className="font-semibold">{title.trim()}</p>}
            {comment.trim() ? (
              <p className="whitespace-pre-wrap text-sm text-muted-foreground">{comment.trim()}</p>
            ) : (
              <p className="text-sm text-muted-foreground">Chưa có nhận xét.</p>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
