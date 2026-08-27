"use client"

import { useCallback, useEffect, useState } from "react"
import { Star } from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { getPublishedReviews } from "@/lib/api/reviews"
import type {
  PublishedReview,
  PublishedReviewListResponse,
  PublishedReviewSummary,
} from "@/types/review"
import { cn } from "@/lib/utils"

const PAGE_SIZE = 5

const CATEGORY_LABELS: Array<{
  key: keyof Pick<
    PublishedReviewSummary,
    | "averageRoomRating"
    | "averageCleanlinessRating"
    | "averageServiceRating"
    | "averageValueRating"
  >
  label: string
}> = [
  { key: "averageRoomRating", label: "Phòng" },
  { key: "averageCleanlinessRating", label: "Vệ sinh" },
  { key: "averageServiceRating", label: "Dịch vụ" },
  { key: "averageValueRating", label: "Giá trị" },
]

function formatRating(value: number | null) {
  if (value === null) return "—"
  return value.toLocaleString("vi-VN", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })
}

function getRatingLabel(value: number | null) {
  if (value === null) return "Chưa có đánh giá"
  if (value >= 4.5) return "Xuất sắc"
  if (value >= 4) return "Rất tốt"
  if (value >= 3) return "Tốt"
  if (value >= 2) return "Khá"
  return "Cần cải thiện"
}

function getInitials(name: string | null) {
  const words = name?.trim().split(/\s+/).filter(Boolean) ?? []
  return words.slice(-2).map((word) => word[0]).join("").toUpperCase() || "K"
}

function formatReviewDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(date)
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) return error.message
  return "Không thể tải đánh giá lúc này. Vui lòng thử lại."
}

function ReviewStars({ rating }: { rating: number }) {
  return (
    <div className="flex gap-0.5" role="img" aria-label={`Đánh giá ${rating} trên 5`}>
      {Array.from({ length: 5 }).map((_, index) => (
        <Star
          key={index}
          className={cn(
            "size-4",
            index < rating ? "fill-primary text-primary" : "text-muted-foreground/30"
          )}
        />
      ))}
    </div>
  )
}

function PublishedReviewsSkeleton() {
  return (
    <div className="flex flex-col gap-4" aria-label="Đang tải đánh giá" role="status">
      <Skeleton className="h-32 w-full rounded-xl" />
      <Skeleton className="h-40 w-full rounded-xl" />
      <Skeleton className="h-40 w-full rounded-xl" />
    </div>
  )
}

function ReviewSummary({ summary }: { summary: PublishedReviewSummary }) {
  const categories = CATEGORY_LABELS
    .map(({ key, label }) => ({ label, value: summary[key] }))
    .filter(({ value }) => value !== null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tổng quan đánh giá</CardTitle>
        <CardDescription>
          Điểm tiêu chí được tính từ toàn bộ đánh giá đã được duyệt.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-8 md:grid-cols-[220px_1fr]">
        <div className="flex items-center gap-4">
          <div className="text-5xl font-bold text-primary">
            {formatRating(summary.averageOverallRating)}
          </div>
          <div className="flex flex-col gap-1">
            <span className="font-semibold text-primary">
              {getRatingLabel(summary.averageOverallRating)}
            </span>
            <span className="text-sm text-muted-foreground">
              {summary.totalReviews.toLocaleString("vi-VN")} đánh giá
            </span>
          </div>
        </div>

        {categories.length > 0 ? (
          <div className="flex flex-col gap-3">
            {categories.map(({ label, value }) => (
              <div key={label} className="grid grid-cols-[76px_1fr_42px] items-center gap-3 text-sm">
                <span className="text-muted-foreground">{label}</span>
                <div className="relative h-2 overflow-hidden rounded-full bg-muted">
                  <div
                    className="absolute inset-y-0 left-0 rounded-full bg-primary"
                    style={{ width: `${Math.min(100, Math.max(0, ((value ?? 0) / 5) * 100))}%` }}
                  />
                </div>
                <span className="text-right font-semibold">{formatRating(value)}</span>
              </div>
            ))}
          </div>
        ) : (
          <p className="flex items-center text-sm text-muted-foreground">
            Chưa có đủ dữ liệu cho các tiêu chí chi tiết.
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function PublishedReviewItem({ review }: { review: PublishedReview }) {
  const customerLabel = review.customerName || "Khách lưu trú"

  return (
    <article className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <Avatar className="size-10">
            <AvatarFallback>{getInitials(review.customerName)}</AvatarFallback>
          </Avatar>
          <div className="flex flex-col gap-1">
            <span className="font-semibold">{customerLabel}</span>
            <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              {review.roomTypeName && <Badge variant="outline">{review.roomTypeName}</Badge>}
              <span>{formatReviewDate(review.createdAt)}</span>
            </div>
          </div>
        </div>
        <ReviewStars rating={review.overallRating} />
      </div>

      {review.title && <h3 className="font-semibold">{review.title}</h3>}
      {review.comment && <p className="whitespace-pre-wrap leading-relaxed">{review.comment}</p>}

      {review.staffReply && (
        <Alert>
          <AlertTitle>Phản hồi từ khách sạn</AlertTitle>
          <AlertDescription className="whitespace-pre-wrap">{review.staffReply}</AlertDescription>
        </Alert>
      )}
    </article>
  )
}

function PublishedReviewList({ response }: { response: PublishedReviewListResponse }) {
  if (response.items.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Chưa có đánh giá ở trang này</CardTitle>
          <CardDescription>
            Hãy quay lại trang trước hoặc xem lại sau khi khách hoàn tất lưu trú.
          </CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Đánh giá gần đây</CardTitle>
        <CardDescription>{response.totalItems.toLocaleString("vi-VN")} đánh giá đã được duyệt</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        {response.items.map((review, index) => (
          <div key={`${review.createdAt}-${review.customerName ?? "guest"}-${index}`} className="flex flex-col gap-6">
            <PublishedReviewItem review={review} />
            {index < response.items.length - 1 && <Separator />}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

export function PublishedReviews() {
  const [response, setResponse] = useState<PublishedReviewListResponse | null>(null)
  const [page, setPage] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const retryReviews = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      const nextResponse = await getPublishedReviews(page, PAGE_SIZE)
      setResponse(nextResponse)
    } catch (loadError) {
      setError(getErrorMessage(loadError))
    } finally {
      setIsLoading(false)
    }
  }, [page])

  useEffect(() => {
    let ignore = false

    getPublishedReviews(page, PAGE_SIZE)
      .then((nextResponse) => {
        if (ignore) return
        setResponse(nextResponse)
        setError(null)
      })
      .catch((loadError: unknown) => {
        if (!ignore) setError(getErrorMessage(loadError))
      })
      .finally(() => {
        if (!ignore) setIsLoading(false)
      })

    return () => {
      ignore = true
    }
  }, [page])

  const hasReviews = Boolean(response && response.summary.totalReviews > 0)
  const showInitialSkeleton = isLoading && response === null

  return (
    <section id="guest-reviews" className="flex scroll-mt-28 flex-col gap-5" aria-labelledby="guest-reviews-title" aria-busy={isLoading}>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 id="guest-reviews-title" className="text-2xl font-bold tracking-tight">Đánh giá của khách</h2>
          <p className="text-muted-foreground">Những chia sẻ thực tế từ khách đã hoàn tất lưu trú.</p>
        </div>
        {isLoading && response && <Skeleton className="h-4 w-24" aria-label="Đang cập nhật đánh giá" />}
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>Không thể tải đánh giá</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
            <span>{error}</span>
            <Button type="button" variant="outline" onClick={() => void retryReviews()} disabled={isLoading}>
              Thử lại
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {showInitialSkeleton ? (
        <PublishedReviewsSkeleton />
      ) : response && hasReviews ? (
        <>
          <ReviewSummary summary={response.summary} />
          <PublishedReviewList response={response} />
        </>
      ) : response && !hasReviews ? (
        <Card>
          <CardHeader>
            <CardTitle>Chưa có đánh giá</CardTitle>
            <CardDescription>Khách sạn chưa có review nào được duyệt để hiển thị.</CardDescription>
          </CardHeader>
        </Card>
      ) : null}

      {response && response.totalPages > 1 && (
        <div className="flex flex-col items-center gap-3">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  href={`#guest-reviews-page-${Math.max(0, page - 1)}`}
                  aria-disabled={page === 0 || isLoading}
                  className={cn((page === 0 || isLoading) && "pointer-events-none opacity-50")}
                  onClick={(event) => {
                    event.preventDefault()
                    if (page > 0 && !isLoading) {
                      setError(null)
                      setIsLoading(true)
                      setPage(page - 1)
                    }
                  }}
                />
              </PaginationItem>
              {Array.from({ length: response.totalPages }).map((_, index) => (
                <PaginationItem key={index}>
                  <PaginationLink
                    href={`#guest-reviews-page-${index}`}
                    isActive={index === page}
                    aria-label={`Trang ${index + 1}`}
                    onClick={(event) => {
                      event.preventDefault()
                      if (index !== page && !isLoading) {
                        setError(null)
                        setIsLoading(true)
                        setPage(index)
                      }
                    }}
                  >
                    {index + 1}
                  </PaginationLink>
                </PaginationItem>
              ))}
              <PaginationItem>
                <PaginationNext
                  href={`#guest-reviews-page-${Math.min(response.totalPages - 1, page + 1)}`}
                  aria-disabled={page >= response.totalPages - 1 || isLoading}
                  className={cn((page >= response.totalPages - 1 || isLoading) && "pointer-events-none opacity-50")}
                  onClick={(event) => {
                    event.preventDefault()
                    if (page < response.totalPages - 1 && !isLoading) {
                      setError(null)
                      setIsLoading(true)
                      setPage(page + 1)
                    }
                  }}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
          <span className="text-sm text-muted-foreground">
            Trang {page + 1} / {response.totalPages}
          </span>
        </div>
      )}
    </section>
  )
}
