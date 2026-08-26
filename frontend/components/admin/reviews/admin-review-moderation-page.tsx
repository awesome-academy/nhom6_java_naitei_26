"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { Loader2, MessageSquareText, Send, ShieldCheck, Star, UserRound } from "lucide-react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge, type BadgeProps } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { DataTable } from "@/components/ui/dataTable"
import { Label } from "@/components/ui/label"
import { Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious } from "@/components/ui/pagination"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { getAdminReviews, getStaffReviews, moderateBookingReview, replyToBookingReview } from "@/lib/api/reviews"
import { getRoomTypes } from "@/lib/api/room-types"
import type { Review, ReviewStatus } from "@/types/review"
import type { RoomType } from "@/types/room-type"

const PAGE_SIZE = 20
const ALL = "ALL"

const statusLabels: Record<ReviewStatus | typeof ALL, string> = {
  ALL: "Tất cả trạng thái",
  PENDING: "Chờ duyệt",
  PUBLISHED: "Đã xuất bản",
  HIDDEN: "Đã ẩn",
  REJECTED: "Đã từ chối",
}

const statusVariants: Record<ReviewStatus, BadgeProps["variant"]> = {
  PENDING: "pending",
  PUBLISHED: "success",
  HIDDEN: "secondary",
  REJECTED: "destructive",
}

const ratingOptions = [5, 4, 3, 2, 1]

function getErrorMessage(error: unknown, fallback = "Không thể thực hiện thao tác. Vui lòng thử lại.") {
  return error instanceof Error && error.message ? error.message : fallback
}

function formatDateTime(value: string | null) {
  if (!value) return "—"
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value))
}

function getPageNumbers(currentPage: number, totalPages: number) {
  if (totalPages <= 5) return Array.from({ length: totalPages }, (_, index) => index)
  if (currentPage <= 2) return [0, 1, 2, -1, totalPages - 1]
  if (currentPage >= totalPages - 3) return [0, -1, totalPages - 3, totalPages - 2, totalPages - 1]
  return [0, -1, currentPage, -1, totalPages - 1]
}

function ReviewStatusBadge({ status }: { status: ReviewStatus }) {
  return <Badge variant={statusVariants[status]}>{statusLabels[status]}</Badge>
}

function RatingValue({ value }: { value: number | null }) {
  return (
    <span className="inline-flex items-center gap-1 whitespace-nowrap">
      <Star data-icon="inline-start" className="fill-current text-amber-500" />
      {value ?? "—"}/5
    </span>
  )
}

export function AdminReviewModerationPage({ mode = "admin" }: { mode?: "admin" | "staff" }) {
  const canModerate = mode === "admin"
  const [reviews, setReviews] = useState<Review[]>([])
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [status, setStatus] = useState<ReviewStatus | typeof ALL>(ALL)
  const [roomTypeCode, setRoomTypeCode] = useState(ALL)
  const [rating, setRating] = useState(ALL)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selectedReview, setSelectedReview] = useState<Review | null>(null)
  const [rejectTarget, setRejectTarget] = useState<Review | null>(null)
  const [moderationReason, setModerationReason] = useState("")
  const [replyDraft, setReplyDraft] = useState("")
  const [mutation, setMutation] = useState<"moderate" | "reply" | null>(null)

  const loadReviews = useCallback(async () => {
    setIsLoading(true)
    setLoadError(null)
    try {
      const response = await (canModerate ? getAdminReviews : getStaffReviews)({
        status: status === ALL ? undefined : status,
        roomTypeCode: roomTypeCode === ALL ? undefined : roomTypeCode,
        rating: rating === ALL ? undefined : Number(rating),
        page,
        size: PAGE_SIZE,
      })
      setReviews(Array.isArray(response.items) ? response.items : [])
      setPage(response.page)
      setTotalPages(response.totalPages)
      setTotalItems(response.totalItems)
    } catch (error) {
      setLoadError(getErrorMessage(error, "Không thể tải danh sách đánh giá."))
    } finally {
      setIsLoading(false)
    }
  }, [canModerate, page, rating, roomTypeCode, status])

  useEffect(() => {
    void getRoomTypes()
      .then(setRoomTypes)
      .catch(() => setRoomTypes([]))
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => void loadReviews(), 0)
    return () => window.clearTimeout(timer)
  }, [loadReviews])

  function resetPageAndSet<T>(setter: (value: T) => void, value: T) {
    setter(value)
    setPage(0)
  }

  function selectReview(review: Review) {
    setSelectedReview(review)
    setReplyDraft(review.staffReply ?? "")
  }

  async function applyModeration(nextStatus: Exclude<ReviewStatus, "PENDING">, reason?: string) {
    if (!selectedReview) return
    setMutation("moderate")
    try {
      const updatedReview = await moderateBookingReview(selectedReview.bookingPublicId, {
        status: nextStatus,
        ...(nextStatus === "REJECTED" ? { moderationReason: reason?.trim() } : {}),
      })
      setSelectedReview(updatedReview)
      setReviews((current) => current.map((review) => review.id === updatedReview.id ? updatedReview : review))
      setRejectTarget(null)
      setModerationReason("")
      toast.success(nextStatus === "PUBLISHED" ? "Đã phê duyệt đánh giá" : nextStatus === "HIDDEN" ? "Đã ẩn đánh giá" : "Đã từ chối đánh giá")
      await loadReviews()
    } catch (error) {
      toast.error(getErrorMessage(error, "Không thể cập nhật trạng thái đánh giá."))
    } finally {
      setMutation(null)
    }
  }

  async function submitReject() {
    const reason = moderationReason.trim()
    if (!reason) {
      toast.error("Vui lòng nhập lý do từ chối.")
      return
    }
    await applyModeration("REJECTED", reason)
  }

  async function submitReply() {
    if (!selectedReview) return
    const reply = replyDraft.trim()
    if (!reply) {
      toast.error("Vui lòng nhập nội dung phản hồi.")
      return
    }
    setMutation("reply")
    try {
      const updatedReview = await replyToBookingReview(selectedReview.bookingPublicId, { staffReply: reply })
      setSelectedReview(updatedReview)
      setReplyDraft(updatedReview.staffReply ?? "")
      setReviews((current) => current.map((review) => review.id === updatedReview.id ? updatedReview : review))
      toast.success("Đã lưu phản hồi cho khách.")
      await loadReviews()
    } catch (error) {
      toast.error(getErrorMessage(error, "Không thể lưu phản hồi."))
    } finally {
      setMutation(null)
    }
  }

  const columns = useMemo(() => [
    {
      key: "customer",
      header: "Khách hàng",
      render: (review: Review) => (
        <div className="flex min-w-44 items-center gap-3">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
            <UserRound />
          </div>
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="truncate font-medium">{review.customerName || "Không rõ tên"}</span>
            <span className="truncate text-xs text-muted-foreground">{review.customerEmail || "—"}</span>
          </div>
        </div>
      ),
    },
    {
      key: "booking",
      header: "Booking",
      render: (review: Review) => (
        <div className="flex min-w-32 flex-col gap-0.5">
          <span className="font-medium">{review.bookingCode || "—"}</span>
          <span className="truncate text-xs text-muted-foreground">{review.bookingPublicId}</span>
        </div>
      ),
    },
    {
      key: "room",
      header: "Phòng",
      render: (review: Review) => (
        <div className="flex min-w-28 flex-col gap-0.5">
          <span>{review.roomNumber ? `Phòng ${review.roomNumber}` : "Nhiều phòng"}</span>
          <span className="text-xs text-muted-foreground">{review.roomTypeCode || "—"}</span>
        </div>
      ),
    },
    {
      key: "overallRating",
      header: "Đánh giá",
      render: (review: Review) => <RatingValue value={review.overallRating} />,
    },
    {
      key: "status",
      header: "Trạng thái",
      render: (review: Review) => <ReviewStatusBadge status={review.status} />,
    },
    {
      key: "createdAt",
      header: "Ngày gửi",
      render: (review: Review) => formatDateTime(review.createdAt),
    },
  ], [])

  const pageNumbers = getPageNumbers(page, totalPages)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">Đánh giá</h1>
          <p className="text-sm text-muted-foreground">
            {canModerate ? "Kiểm duyệt nội dung đánh giá và phản hồi khách hàng." : "Xem đánh giá và phản hồi khách hàng."}
          </p>
        </div>
        <Badge variant="outline" className="gap-2 px-3 py-1.5">
          <ShieldCheck data-icon="inline-start" /> {totalItems} đánh giá
        </Badge>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Danh sách đánh giá</CardTitle>
          <CardDescription>{canModerate ? "Chọn một dòng để xem đầy đủ nội dung và xử lý moderation." : "Chọn một dòng để xem đầy đủ nội dung và phản hồi khách hàng."}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="grid gap-3 md:grid-cols-3">
            <Select value={status} onValueChange={(value) => resetPageAndSet(setStatus, value as ReviewStatus | typeof ALL)}>
              <SelectTrigger aria-label="Lọc theo trạng thái"><SelectValue /></SelectTrigger>
              <SelectContent>
                {(Object.keys(statusLabels) as Array<ReviewStatus | typeof ALL>).map((value) => (
                  <SelectItem key={value} value={value}>{statusLabels[value]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={roomTypeCode} onValueChange={(value) => resetPageAndSet(setRoomTypeCode, value)}>
              <SelectTrigger aria-label="Lọc theo loại phòng"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Tất cả loại phòng</SelectItem>
                {roomTypes.map((roomType) => <SelectItem key={roomType.code} value={roomType.code}>{roomType.code} · {roomType.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={rating} onValueChange={(value) => resetPageAndSet(setRating, value)}>
              <SelectTrigger aria-label="Lọc theo số sao"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Tất cả số sao</SelectItem>
                {ratingOptions.map((value) => <SelectItem key={value} value={String(value)}>{value} sao</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          {loadError ? (
            <Alert variant="destructive">
              <AlertTitle>Không thể tải đánh giá</AlertTitle>
              <AlertDescription className="flex flex-col gap-3">
                <span>{loadError}</span>
                <Button variant="outline" className="w-fit" onClick={() => void loadReviews()}>Thử lại</Button>
              </AlertDescription>
            </Alert>
          ) : isLoading ? (
            <Skeleton className="h-96 w-full" />
          ) : (
            <DataTable
              columns={columns}
              data={reviews}
              keyExtractor={(review) => String(review.id)}
              onRowClick={selectReview}
              emptyMessage="Không tìm thấy đánh giá phù hợp"
            />
          )}

          {!isLoading && !loadError && totalPages > 1 && (
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious href="#" aria-disabled={page === 0} className={page === 0 ? "pointer-events-none opacity-50" : undefined} onClick={(event) => { event.preventDefault(); if (page > 0) setPage(page - 1) }} />
                </PaginationItem>
                {pageNumbers.map((pageNumber, index) => (
                  <PaginationItem key={`${pageNumber}-${index}`}>
                    {pageNumber < 0 ? <PaginationEllipsis /> : <PaginationLink href="#" isActive={pageNumber === page} onClick={(event) => { event.preventDefault(); setPage(pageNumber) }}>{pageNumber + 1}</PaginationLink>}
                  </PaginationItem>
                ))}
                <PaginationItem>
                  <PaginationNext href="#" aria-disabled={page >= totalPages - 1} className={page >= totalPages - 1 ? "pointer-events-none opacity-50" : undefined} onClick={(event) => { event.preventDefault(); if (page < totalPages - 1) setPage(page + 1) }} />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          )}
        </CardContent>
      </Card>

      <ReviewDetailSheet
        review={selectedReview}
        replyDraft={replyDraft}
        mutation={mutation}
        onReplyDraftChange={setReplyDraft}
        onOpenChange={(open) => { if (!open && !mutation) setSelectedReview(null) }}
        onModerate={(nextStatus) => void applyModeration(nextStatus)}
        onReply={() => void submitReply()}
        onReject={() => { setRejectTarget(selectedReview); setModerationReason("") }}
        canModerate={canModerate}
      />

      {canModerate && <Dialog open={rejectTarget !== null} onOpenChange={(open) => { if (!open && !mutation) setRejectTarget(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Từ chối đánh giá</DialogTitle>
            <DialogDescription>Lý do này sẽ được lưu để nhân viên biết vì sao đánh giá bị từ chối.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="moderation-reason">Lý do từ chối</Label>
            <Textarea id="moderation-reason" value={moderationReason} maxLength={1000} showCount onChange={(event) => setModerationReason(event.target.value)} placeholder="Ví dụ: Nội dung vi phạm chính sách đánh giá" />
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={mutation !== null} onClick={() => setRejectTarget(null)}>Hủy</Button>
            <Button variant="destructive" disabled={mutation !== null} onClick={() => void submitReject()}>
              {mutation === "moderate" && <Loader2 className="animate-spin" />}
              Từ chối
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </div>
  )
}

interface ReviewDetailSheetProps {
  review: Review | null
  replyDraft: string
  mutation: "moderate" | "reply" | null
  onReplyDraftChange: (value: string) => void
  onOpenChange: (open: boolean) => void
  onModerate: (status: Exclude<ReviewStatus, "PENDING">) => void
  onReply: () => void
  onReject: () => void
  canModerate: boolean
}

function ReviewDetailSheet({ review, replyDraft, mutation, onReplyDraftChange, onOpenChange, onModerate, onReply, onReject, canModerate }: ReviewDetailSheetProps) {
  return (
    <Sheet open={review !== null} onOpenChange={onOpenChange}>
      <SheetContent className="max-w-2xl">
        {review && (
          <>
            <SheetHeader className="border-b px-6 py-5 pr-12">
              <div className="flex items-start justify-between gap-3">
                <div className="flex min-w-0 flex-col gap-1">
                  <SheetTitle className="flex items-center gap-2"><MessageSquareText data-icon="inline-start" /> Chi tiết đánh giá</SheetTitle>
                  <SheetDescription className="truncate">Review #{review.id} · {review.bookingCode || review.bookingPublicId}</SheetDescription>
                </div>
                <ReviewStatusBadge status={review.status} />
              </div>
            </SheetHeader>
            <div className="flex-1 overflow-y-auto px-6 py-5">
              <div className="flex flex-col gap-6">
                <section className="flex flex-col gap-3">
                  <h3 className="text-sm font-semibold">Khách hàng</h3>
                  <DetailGrid rows={[
                    ["Họ tên", review.customerName],
                    ["Email", review.customerEmail],
                  ]} />
                </section>
                <Separator />
                <section className="flex flex-col gap-3">
                  <h3 className="text-sm font-semibold">Booking và phòng</h3>
                  <DetailGrid rows={[
                    ["Booking public ID", review.bookingPublicId],
                    ["Booking code", review.bookingCode],
                    ["Phòng", review.roomNumber ? `Phòng ${review.roomNumber}` : "Nhiều phòng / chưa gán"],
                    ["Loại phòng", review.roomTypeCode ? `${review.roomTypeCode} · ${review.roomTypeName || ""}` : "—"],
                  ]} />
                </section>
                <Separator />
                <section className="flex flex-col gap-3">
                  <h3 className="text-sm font-semibold">Điểm đánh giá</h3>
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <RatingRow label="Tổng thể" value={review.overallRating} />
                    <RatingRow label="Phòng" value={review.roomRating} />
                    <RatingRow label="Vệ sinh" value={review.cleanlinessRating} />
                    <RatingRow label="Dịch vụ" value={review.serviceRating} />
                    <RatingRow label="Giá trị" value={review.valueRating} />
                  </div>
                </section>
                <Separator />
                <section className="flex flex-col gap-3">
                  <h3 className="text-sm font-semibold">Nội dung khách hàng</h3>
                  <div className="flex flex-col gap-3 rounded-lg border p-4">
                    <p className="font-medium">{review.title || "Không có tiêu đề"}</p>
                    <p className="whitespace-pre-wrap text-sm text-muted-foreground">{review.comment || "Không có bình luận."}</p>
                  </div>
                  <DetailGrid rows={[["Ngày gửi", formatDateTime(review.createdAt)], ["Cập nhật", formatDateTime(review.updatedAt)]]} />
                </section>
                {canModerate && review.moderationReason && (
                  <section className="flex flex-col gap-2">
                    <h3 className="text-sm font-semibold">Lý do moderation</h3>
                    <p className="whitespace-pre-wrap rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm">{review.moderationReason}</p>
                  </section>
                )}
                <Separator />
                <section className="flex flex-col gap-3">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold">Phản hồi của khách sạn</h3>
                    {review.staffRepliedAt && <span className="text-xs text-muted-foreground">{formatDateTime(review.staffRepliedAt)}</span>}
                  </div>
                  <Textarea value={replyDraft} maxLength={10000} showCount onChange={(event) => onReplyDraftChange(event.target.value)} placeholder="Viết phản hồi cho khách hàng..." disabled={mutation !== null} />
                  <Button onClick={onReply} disabled={mutation !== null} className="self-end">
                    {mutation === "reply" ? <Loader2 className="animate-spin" /> : <Send data-icon="inline-start" />}
                    Lưu phản hồi
                  </Button>
                  {review.staffReply && <p className="text-xs text-muted-foreground">Phản hồi hiện tại sẽ được cập nhật khi lưu nội dung mới.</p>}
                </section>
                {canModerate && <>
                  <Separator />
                  <div className="flex flex-wrap justify-end gap-2 pt-1">
                    <Button variant="outline" disabled={mutation !== null} onClick={() => onModerate("HIDDEN")}>Ẩn</Button>
                    <Button variant="destructive" disabled={mutation !== null} onClick={onReject}>Từ chối</Button>
                    <Button disabled={mutation !== null} onClick={() => onModerate("PUBLISHED")}>Phê duyệt</Button>
                  </div>
                </>}
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}

function DetailGrid({ rows }: { rows: Array<[string, string | null]> }) {
  return (
    <dl className="flex flex-col gap-2 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="flex items-start justify-between gap-4">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="max-w-[65%] break-words text-right font-medium">{value || "—"}</dd>
        </div>
      ))}
    </dl>
  )
}

function RatingRow({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="flex items-center justify-between rounded-md bg-muted/40 px-3 py-2">
      <span className="text-muted-foreground">{label}</span>
      <RatingValue value={value} />
    </div>
  )
}
