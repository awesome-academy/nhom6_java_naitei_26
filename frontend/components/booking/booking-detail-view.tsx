"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import {
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  Clock3,
  CreditCard,
  Hotel,
  Mail,
  Phone,
  ReceiptText,
  UserRound,
  UsersRound,
} from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge, getBookingStatusVariant, getPaymentStatusVariant } from "@/components/ui/badge"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { getMyBookingDetail } from "@/lib/api/booking"
import type {
  Booking,
  BookingDetail,
  BookingRoom,
  BookingStatus,
  BookingStatusHistory,
} from "@/types/booking"

const bookingStatusLabels: Record<BookingStatus, string> = {
  PENDING: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  CHECKED_IN: "Đang lưu trú",
  CHECKED_OUT: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
  EXPIRED: "Đã hết hạn",
}

const paymentStatusLabels: Record<string, string> = {
  UNPAID: "Chưa thanh toán",
  PARTIALLY_PAID: "Thanh toán một phần",
  PAID: "Đã thanh toán",
  PARTIALLY_REFUNDED: "Hoàn một phần",
  REFUNDED: "Đã hoàn tiền",
}

const roomStatusLabels: Record<string, string> = {
  RESERVED: "Đã giữ phòng",
  OCCUPIED: "Đang sử dụng",
  COMPLETED: "Đã hoàn tất",
  RELEASED: "Đã giải phóng",
  MOVED_OUT: "Đã chuyển phòng",
}

const sourceLabels: Record<string, string> = {
  WEBSITE: "Website",
  WALK_IN: "Khách trực tiếp",
  PHONE: "Điện thoại",
  BOOKING_COM: "Booking.com",
  AGODA: "Agoda",
  STAFF_MANUAL: "Nhân viên tạo",
}

const historySourceLabels: Record<string, string> = {
  MANUAL: "Thao tác trực tiếp",
  PAYMENT_CALLBACK: "Xác nhận thanh toán",
  HOLD_EXPIRY_JOB: "Hết thời gian giữ phòng",
  NO_SHOW_JOB: "Xử lý khách không đến",
  OTA_IMPORT: "Đồng bộ OTA",
  SYSTEM_OTHER: "Hệ thống",
}

function formatMoney(value: number | string, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(Number(value) || 0)
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(`${value}T00:00:00`))
}

function formatDateTime(value: string | null) {
  if (!value) return "Chưa ghi nhận"

  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value))
}

function getRoomNights(room: BookingRoom) {
  if (room.nights.length > 0) return room.nights.length

  const checkIn = new Date(`${room.checkInDate}T00:00:00`)
  const checkOut = new Date(`${room.checkOutDate}T00:00:00`)
  return Math.max(0, Math.round((checkOut.getTime() - checkIn.getTime()) / 86_400_000))
}

function getStayRange(booking: Booking) {
  const checkInDates = booking.rooms.map((room) => room.checkInDate).sort()
  const checkOutDates = booking.rooms.map((room) => room.checkOutDate).sort()
  const firstCheckIn = checkInDates[0]
  const lastCheckOut = checkOutDates[checkOutDates.length - 1]

  if (!firstCheckIn || !lastCheckOut) return "Chưa có ngày lưu trú"
  return `${formatDate(firstCheckIn)} - ${formatDate(lastCheckOut)}`
}

function getErrorMessage(error: unknown) {
  const apiError = error as Error & { status?: number }
  if (apiError.status === 404) {
    return "Booking không tồn tại hoặc không thuộc tài khoản của bạn."
  }
  return apiError.message || "Không thể tải chi tiết booking."
}

export function BookingDetailView({ publicId }: { publicId: string }) {
  const [detail, setDetail] = useState<BookingDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let ignore = false

    async function loadBookingDetail() {
      setIsLoading(true)
      setError(null)
      try {
        const response = await getMyBookingDetail(publicId)
        if (!ignore) setDetail(response)
      } catch (loadError) {
        if (!ignore) setError(getErrorMessage(loadError))
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }

    loadBookingDetail()

    return () => {
      ignore = true
    }
  }, [publicId])

  if (isLoading) return <BookingDetailSkeleton />

  if (error || !detail) {
    return (
      <div className="flex flex-col gap-6">
        <BookingBreadcrumb current="Không tìm thấy" />
        <Alert variant="destructive">
          <ReceiptText />
          <AlertTitle>Không thể mở booking</AlertTitle>
          <AlertDescription>{error ?? "Không tìm thấy dữ liệu booking."}</AlertDescription>
        </Alert>
        <div>
          <Button asChild variant="outline">
            <Link href="/profile/bookings">
              <ArrowLeft data-icon="inline-start" />
              Quay lại danh sách
            </Link>
          </Button>
        </div>
      </div>
    )
  }

  const { booking } = detail

  return (
    <div className="flex flex-col gap-6">
      <BookingBreadcrumb current={booking.bookingCode} />

      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start">
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight">Chi tiết booking</h1>
            <Badge variant={getBookingStatusVariant(booking.status)}>
              {bookingStatusLabels[booking.status]}
            </Badge>
            <Badge variant={getPaymentStatusVariant(booking.paymentStatus)}>
              {paymentStatusLabels[booking.paymentStatus] ?? booking.paymentStatus}
            </Badge>
          </div>
          <p className="font-mono text-sm text-muted-foreground">{booking.bookingCode}</p>
        </div>
        <Button asChild variant="outline">
          <Link href="/profile/bookings">
            <ArrowLeft data-icon="inline-start" />
            Danh sách booking
          </Link>
        </Button>
      </div>

      {booking.status === "PENDING" && booking.holdExpiresAt && (
        <Alert>
          <Clock3 />
          <AlertTitle>Phòng đang được giữ tạm thời</AlertTitle>
          <AlertDescription>
            Hoàn tất thanh toán trước {formatDateTime(booking.holdExpiresAt)} để giữ booking.
          </AlertDescription>
        </Alert>
      )}

      {booking.status === "CANCELLED" && detail.cancellationReason && (
        <Alert variant="destructive">
          <ReceiptText />
          <AlertTitle>Booking đã hủy</AlertTitle>
          <AlertDescription>{detail.cancellationReason}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="flex min-w-0 flex-col gap-6">
          <StayDetailsCard booking={booking} />
          <BookingTimeline history={detail.statusHistory} currentStatus={booking.status} />
        </div>

        <div className="flex flex-col gap-6">
          <PaymentSummaryCard detail={detail} />
          <ContactCard booking={booking} />
          <BookingNotesCard detail={detail} />
        </div>
      </div>
    </div>
  )
}

function BookingBreadcrumb({ current }: { current: string }) {
  return (
    <Breadcrumb>
      <BreadcrumbList>
        <BreadcrumbItem>
          <BreadcrumbLink href="/profile/bookings">Đơn đặt phòng</BreadcrumbLink>
        </BreadcrumbItem>
        <BreadcrumbSeparator />
        <BreadcrumbItem>
          <BreadcrumbPage>{current}</BreadcrumbPage>
        </BreadcrumbItem>
      </BreadcrumbList>
    </Breadcrumb>
  )
}

function StayDetailsCard({ booking }: { booking: Booking }) {
  const totalNights = useMemo(
    () => booking.rooms.reduce((sum, room) => sum + getRoomNights(room), 0),
    [booking.rooms],
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>Thông tin lưu trú</CardTitle>
        <CardDescription>
          {getStayRange(booking)} · {booking.rooms.length} phòng · {totalNights} đêm-phòng
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {booking.rooms.length === 0 ? (
          <Alert>
            <Hotel />
            <AlertTitle>Chưa có phòng</AlertTitle>
            <AlertDescription>Booking này chưa có thông tin phòng lưu trú.</AlertDescription>
          </Alert>
        ) : (
          booking.rooms.map((room, index) => (
            <RoomDetailCard
              key={room.bookingRoomId}
              room={room}
              roomIndex={index}
              currency={booking.currency}
            />
          ))
        )}
      </CardContent>
    </Card>
  )
}

function RoomDetailCard({
  room,
  roomIndex,
  currency,
}: {
  room: BookingRoom
  roomIndex: number
  currency: string
}) {
  return (
    <Card className="shadow-none">
      <CardHeader>
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
          <div className="flex flex-col gap-1">
            <CardTitle>Phòng {roomIndex + 1}: {room.roomTypeName}</CardTitle>
            <CardDescription>{room.roomTypeCode} · {room.roomNumber ?? "Chưa phân số phòng"}</CardDescription>
          </div>
          <div className="flex flex-col items-start gap-2 sm:items-end">
            <Badge variant="outline">{roomStatusLabels[room.status] ?? room.status}</Badge>
            <span className="font-semibold">{formatMoney(room.roomSubtotal, currency)}</span>
          </div>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <DetailItem icon={CalendarDays} label="Nhận phòng" value={formatDate(room.checkInDate)} />
          <DetailItem icon={CalendarDays} label="Trả phòng" value={formatDate(room.checkOutDate)} />
          <DetailItem icon={Clock3} label="Số đêm" value={`${getRoomNights(room)} đêm`} />
          <DetailItem icon={UsersRound} label="Khách dự kiến" value={`${room.guestCount} khách`} />
        </div>

        <Separator />

        <div className="grid gap-4 sm:grid-cols-2">
          <DetailItem
            icon={ReceiptText}
            label="Chính sách hủy"
            value={room.cancellationPolicyName ?? "Chưa có chính sách"}
          />
          <DetailItem
            icon={CreditCard}
            label="Hình thức thanh toán"
            value={room.paymentOption === "PAY_AT_HOTEL" ? "Thanh toán tại khách sạn" : "Thanh toán trực tuyến"}
          />
        </div>

        <Separator />

        <div className="flex flex-col gap-3">
          <p className="text-sm font-semibold">Giá từng đêm</p>
          {room.nights.length === 0 ? (
            <p className="text-sm text-muted-foreground">Chưa có dữ liệu giá theo đêm.</p>
          ) : (
            <div className="flex flex-col gap-2">
              {room.nights.map((night) => (
                <div
                  key={night.stayDate}
                  className="flex items-center justify-between gap-3 rounded-md bg-muted px-3 py-2 text-sm"
                >
                  <span>Đêm {formatDate(night.stayDate)}</span>
                  <span className="font-medium">{formatMoney(night.price, currency)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function BookingTimeline({
  history,
  currentStatus,
}: {
  history: BookingStatusHistory[]
  currentStatus: BookingStatus
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Tiến trình booking</CardTitle>
        <CardDescription>Lịch sử trạng thái được ghi nhận theo thời gian.</CardDescription>
      </CardHeader>
      <CardContent>
        {history.length === 0 ? (
          <Alert>
            <Clock3 />
            <AlertTitle>Chưa có lịch sử trạng thái</AlertTitle>
            <AlertDescription>
              Trạng thái hiện tại: {bookingStatusLabels[currentStatus]}.
            </AlertDescription>
          </Alert>
        ) : (
          <ol className="flex flex-col gap-4">
            {history.map((event, index) => (
              <li key={`${event.toStatus}-${event.createdAt ?? index}`} className="flex gap-3">
                <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-primary" />
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{bookingStatusLabels[event.toStatus]}</span>
                    <span className="text-xs text-muted-foreground">{formatDateTime(event.createdAt)}</span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {historySourceLabels[event.source] ?? event.source}
                    {event.actorType === "SYSTEM" ? " · Hệ thống" : " · Người dùng"}
                  </p>
                  {event.reason && <p className="text-sm">{event.reason}</p>}
                </div>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}

function PaymentSummaryCard({ detail }: { detail: BookingDetail }) {
  const { booking } = detail

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tổng thanh toán</CardTitle>
        <CardDescription>Chi tiết số tiền của booking.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <SummaryRow label="Tiền phòng" value={formatMoney(booking.roomsTotal, booking.currency)} />
        <SummaryRow label="Dịch vụ" value={formatMoney(detail.servicesTotal, booking.currency)} />
        <SummaryRow label="Thuế và phí" value={formatMoney(booking.taxTotal, booking.currency)} />
        {detail.discountTotal > 0 && (
          <SummaryRow label="Giảm giá" value={`-${formatMoney(detail.discountTotal, booking.currency)}`} />
        )}
        <Separator />
        <SummaryRow label="Tổng cộng" value={formatMoney(booking.totalAmount, booking.currency)} emphasized />
        <SummaryRow
          label={`Tiền cọc (${booking.depositPercentSnapshot}%)`}
          value={formatMoney(booking.requiredDepositAmount, booking.currency)}
        />
        <SummaryRow label="Đã thanh toán" value={formatMoney(detail.paidAmount, booking.currency)} />
        {detail.refundedAmount > 0 && (
          <SummaryRow label="Đã hoàn" value={formatMoney(detail.refundedAmount, booking.currency)} />
        )}
      </CardContent>
      <CardFooter className="justify-between gap-3">
        <span className="text-sm text-muted-foreground">Trạng thái</span>
        <Badge variant={getPaymentStatusVariant(booking.paymentStatus)}>
          {paymentStatusLabels[booking.paymentStatus] ?? booking.paymentStatus}
        </Badge>
      </CardFooter>
    </Card>
  )
}

function ContactCard({ booking }: { booking: Booking }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Thông tin liên hệ</CardTitle>
        <CardDescription>Đầu mối được lưu tại thời điểm đặt phòng.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <DetailItem icon={UserRound} label="Người liên hệ" value={booking.contactName} />
        <DetailItem icon={Mail} label="Email" value={booking.contactEmail ?? "Không có"} />
        <DetailItem icon={Phone} label="Số điện thoại" value={booking.contactPhone ?? "Không có"} />
        <DetailItem
          icon={UsersRound}
          label="Số khách"
          value={`${booking.adults} người lớn · ${booking.children} trẻ em`}
        />
      </CardContent>
    </Card>
  )
}

function BookingNotesCard({ detail }: { detail: BookingDetail }) {
  const { booking } = detail

  return (
    <Card>
      <CardHeader>
        <CardTitle>Thông tin booking</CardTitle>
        <CardDescription>Thông tin bổ sung và nguồn tạo đơn.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <SummaryRow label="Nguồn" value={sourceLabels[booking.sourceCode] ?? booking.sourceCode} />
        <SummaryRow label="Ngày tạo" value={formatDateTime(booking.createdAt)} />
        <Separator />
        <div className="flex flex-col gap-1">
          <span className="text-sm text-muted-foreground">Yêu cầu đặc biệt</span>
          <span className="text-sm">{detail.specialRequests ?? "Không có yêu cầu đặc biệt"}</span>
        </div>
      </CardContent>
      <CardFooter>
        <Button asChild className="w-full">
          <Link href="/booking">
            <Hotel data-icon="inline-start" />
            Đặt phòng mới
          </Link>
        </Button>
      </CardFooter>
    </Card>
  )
}

function DetailItem({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof CalendarDays
  label: string
  value: string
}) {
  return (
    <div className="flex items-start gap-3">
      <Icon className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
      <div className="flex min-w-0 flex-col gap-1">
        <span className="text-xs uppercase text-muted-foreground">{label}</span>
        <span className="break-words text-sm font-medium">{value}</span>
      </div>
    </div>
  )
}

function SummaryRow({
  label,
  value,
  emphasized = false,
}: {
  label: string
  value: string
  emphasized?: boolean
}) {
  return (
    <div className="flex items-start justify-between gap-3 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className={emphasized ? "text-base font-semibold" : "font-medium"}>{value}</span>
    </div>
  )
}

function BookingDetailSkeleton() {
  return (
    <div className="flex flex-col gap-6" aria-label="Đang tải chi tiết booking">
      <Skeleton className="h-5 w-64" />
      <div className="flex flex-col gap-3">
        <Skeleton className="h-8 w-52" />
        <Skeleton className="h-4 w-36" />
      </div>
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="flex flex-col gap-6">
          <Skeleton className="h-[420px] w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
        <div className="flex flex-col gap-6">
          <Skeleton className="h-80 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      </div>
    </div>
  )
}
