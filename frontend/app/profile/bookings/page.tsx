"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import {
  CalendarDays,
  Clock,
  CreditCard,
  Eye,
  Hotel,
  Loader2,
  ReceiptText,
  Search,
  Trash2,
  Users,
} from "lucide-react"
import { toast } from "sonner"

import { Badge, getBookingStatusVariant, getPaymentStatusVariant } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/action-dialog"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { cancelBooking, deletePendingBooking, getMyBookings } from "@/lib/api/booking"
import type { Booking, BookingRoom, BookingStatus } from "@/types/booking"

type DeleteTarget =
  | { type: "booking"; booking: Booking }

const bookingStatusLabels: Record<BookingStatus, string> = {
  PENDING: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  CHECKED_IN: "Đang ở",
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

const statusFilters = [
  { value: "all", label: "Tất cả" },
  { value: "active", label: "Đang giữ / sắp tới" },
  { value: "staying", label: "Đang ở" },
  { value: "completed", label: "Đã hoàn tất" },
  { value: "cancelled", label: "Đã hủy / hết hạn" },
]

function money(value: number | string, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(Number(value) || 0)
}

function displayDate(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(`${value}T00:00:00`))
}

function displayDateTime(value: string) {
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
  return `${displayDate(firstCheckIn)} - ${displayDate(lastCheckOut)}`
}

function getTotalNights(booking: Booking) {
  return booking.rooms.reduce((sum, room) => sum + getRoomNights(room), 0)
}

function getPaymentLabel(status: string) {
  return paymentStatusLabels[status] ?? status
}

function canPayBooking(booking: Booking) {
  return booking.status === "PENDING"
    && ["UNPAID", "PARTIALLY_PAID"].includes(booking.paymentStatus)
    && booking.holdExpiresAt !== null
    && new Date(booking.holdExpiresAt).getTime() > Date.now()
}

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Không thể thực hiện thao tác"
}

function matchesStatusFilter(booking: Booking, statusFilter: string) {
  if (statusFilter === "all") return true
  if (statusFilter === "active") return ["PENDING", "CONFIRMED"].includes(booking.status)
  if (statusFilter === "staying") return booking.status === "CHECKED_IN"
  if (statusFilter === "completed") return booking.status === "CHECKED_OUT"
  if (statusFilter === "cancelled") {
    return ["CANCELLED", "NO_SHOW", "EXPIRED"].includes(booking.status)
  }
  return true
}

export default function ProfileBookingsPage() {
  const [bookings, setBookings] = useState<Booking[]>([])
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("all")
  const [isLoading, setIsLoading] = useState(true)
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [cancelTarget, setCancelTarget] = useState<Booking | null>(null)
  const [isCancelling, setIsCancelling] = useState(false)

  useEffect(() => {
    let ignore = false

    async function loadBookings() {
      setIsLoading(true)
      try {
        const data = await getMyBookings()
        if (!ignore) setBookings(data)
      } catch {
        if (!ignore) {
          toast.error("Không thể tải danh sách đặt phòng")
        }
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }

    loadBookings()

    return () => {
      ignore = true
    }
  }, [])

  const filteredBookings = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()

    return bookings.filter((booking) => {
      const searchText = [
        booking.bookingCode,
        booking.contactName,
        booking.contactEmail,
        ...booking.rooms.flatMap((room) => [
          room.roomNumber ?? "",
          room.roomTypeCode,
          room.roomTypeName,
          room.cancellationPolicyName ?? "",
        ]),
      ].join(" ").toLowerCase()

      const matchesSearch = normalizedSearch.length === 0 || searchText.includes(normalizedSearch)
      return matchesSearch && matchesStatusFilter(booking, statusFilter)
    })
  }, [bookings, search, statusFilter])

  const stats = useMemo(() => ({
    total: bookings.length,
    active: bookings.filter((booking) => ["PENDING", "CONFIRMED"].includes(booking.status)).length,
    staying: bookings.filter((booking) => booking.status === "CHECKED_IN").length,
    completed: bookings.filter((booking) => booking.status === "CHECKED_OUT").length,
  }), [bookings])

  async function handleConfirmDelete() {
    if (!deleteTarget) return

    setIsDeleting(true)
    try {
      if (deleteTarget.type === "booking") {
        await deletePendingBooking(deleteTarget.booking.publicId)
        setBookings((current) => current.filter((booking) => (
          booking.publicId !== deleteTarget.booking.publicId
        )))
        toast.success("Đã xóa booking chờ thanh toán")
      }
      setDeleteTarget(null)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsDeleting(false)
    }
  }

  async function handleConfirmCancel() {
    if (!cancelTarget || isCancelling) return

    setIsCancelling(true)
    try {
      const updated = await cancelBooking(cancelTarget.publicId)
      setBookings((current) => current.map((item) => item.publicId === updated.publicId ? updated : item))
      setCancelTarget(null)
      toast.success("Đã gửi yêu cầu hủy booking")
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsCancelling(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">Đơn đặt phòng</h1>
          <p className="text-sm text-muted-foreground">
            Theo dõi các booking đã lưu từ hệ thống.
          </p>
        </div>
        <Button asChild>
          <Link href="/booking">
            <Hotel data-icon="inline-start" />
            Đặt phòng mới
          </Link>
        </Button>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <StatCard icon={ReceiptText} label="Tổng đơn" value={stats.total} />
        <StatCard icon={Clock} label="Đang giữ / sắp tới" value={stats.active} />
        <StatCard icon={Hotel} label="Đang ở" value={stats.staying} />
        <StatCard icon={CalendarDays} label="Đã hoàn tất" value={stats.completed} />
      </div>

      <div className="flex flex-col gap-3 md:flex-row md:items-center">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm theo mã booking hoặc thông tin liên hệ..."
            className="pl-10"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-full md:w-[220px]">
            <SelectValue placeholder="Trạng thái" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {statusFilters.map((filter) => (
                <SelectItem key={filter.value} value={filter.value}>
                  {filter.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-44 w-full" />
          ))}
        </div>
      ) : bookings.length === 0 ? (
        <EmptyBookings />
      ) : filteredBookings.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center gap-3 py-12 text-center">
            <p className="text-lg font-semibold">Không có</p>
            <p className="max-w-md text-sm text-muted-foreground">
              Không có booking nào khớp với bộ lọc hiện tại.
            </p>
            <Button variant="outline" onClick={() => {
              setSearch("")
              setStatusFilter("all")
            }}>
              Xóa bộ lọc
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-4">
          {filteredBookings.map((booking) => (
            <BookingCard
              key={booking.publicId}
              booking={booking}
              onRequestDeleteBooking={(targetBooking) => {
                setDeleteTarget({ type: "booking", booking: targetBooking })
              }}
              onCancel={async (targetBooking) => {
                setCancelTarget(targetBooking)
              }}
            />
          ))}
        </div>
      )}

      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !isDeleting) setDeleteTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              Xóa booking chờ thanh toán?
            </DialogTitle>
            <DialogDescription>
              Booking {deleteTarget?.booking.bookingCode} sẽ bị xóa vĩnh viễn và giải phóng các phòng đang giữ.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={isDeleting}
              onClick={() => setDeleteTarget(null)}
            >
              Giữ lại
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={isDeleting}
              onClick={handleConfirmDelete}
            >
              {isDeleting ? <Loader2 className="animate-spin" /> : <Trash2 />}
              Xóa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={cancelTarget !== null}
        onOpenChange={(open) => {
          if (!open && !isCancelling) setCancelTarget(null)
        }}
        title="Hủy booking?"
        description="Booking sẽ được hủy theo chính sách hủy hiện tại của phòng. Bạn có muốn tiếp tục không?"
        confirmLabel="Xác nhận hủy"
        onConfirm={() => void handleConfirmCancel()}
        isLoading={isCancelling}
        destructive
      />
    </div>
  )
}

function StatCard({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof ReceiptText
  label: string
  value: number
}) {
  return (
    <Card>
      <CardContent className="flex items-center gap-3 p-4">
        <div className="flex size-10 items-center justify-center rounded-lg bg-muted text-muted-foreground">
          <Icon />
        </div>
        <div>
          <p className="text-2xl font-semibold leading-none">{value}</p>
          <p className="mt-1 text-sm text-muted-foreground">{label}</p>
        </div>
      </CardContent>
    </Card>
  )
}

function EmptyBookings() {
  return (
    <Card>
      <CardContent className="flex flex-col items-center justify-center gap-4 py-16 text-center">
        <div className="flex size-14 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <CalendarDays />
        </div>
        <div className="flex flex-col gap-1">
          <p className="text-lg font-semibold">Không có</p>
          <p className="max-w-md text-sm text-muted-foreground">
            Bạn chưa có đơn đặt phòng nào. Chọn phòng để tạo booking đầu tiên.
          </p>
        </div>
        <Button asChild>
          <Link href="/booking">
            <Hotel data-icon="inline-start" />
            Chuyển đến trang booking
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}

function BookingCard({
  booking,
  onRequestDeleteBooking,
  onCancel,
}: {
  booking: Booking
  onRequestDeleteBooking: (booking: Booking) => void
  onCancel: (booking: Booking) => void
}) {
  const roomCount = booking.rooms.length
  const nightCount = getTotalNights(booking)
  const guestCount = booking.adults + booking.children

  return (
    <Card className="overflow-hidden">
      <CardHeader className="gap-3 border-b pb-4">
        <div className="flex flex-col justify-between gap-3 md:flex-row md:items-start">
          <div className="flex flex-col gap-2">
            <CardTitle className="text-xl">
              Mã đặt phòng: {booking.bookingCode}
            </CardTitle>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={getBookingStatusVariant(booking.status)}>
                {bookingStatusLabels[booking.status] ?? booking.status}
              </Badge>
              <Badge variant={getPaymentStatusVariant(booking.paymentStatus)}>
                {getPaymentLabel(booking.paymentStatus)}
              </Badge>
            </div>
          </div>
          <div className="flex flex-col gap-1 md:text-right">
            <span className="text-xl font-semibold">
              {money(booking.totalAmount, booking.currency)}
            </span>
            <span className="text-sm text-muted-foreground">
              VAT & phí: {money(booking.taxTotal, booking.currency)}
            </span>
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex flex-col gap-4 p-5">
        <div className="grid gap-4 md:grid-cols-4">
          <InfoBlock icon={CalendarDays} label="Ngày lưu trú" value={getStayRange(booking)} />
          <InfoBlock icon={Clock} label="Số đêm" value={`${nightCount} đêm`} />
          <InfoBlock icon={Hotel} label="Số phòng" value={`${roomCount} phòng`} />
          <InfoBlock icon={Users} label="Khách dự kiến" value={`${guestCount} khách`} />
        </div>

        <div className="flex flex-col justify-between gap-2 text-sm text-muted-foreground md:flex-row">
          <span>Người liên hệ: {booking.contactName} · {booking.contactPhone ?? booking.contactEmail}</span>
          <span>Tạo lúc {displayDateTime(booking.createdAt)}</span>
        </div>

        {booking.status === "PENDING" && booking.holdExpiresAt && (
          <div className="rounded-md bg-muted p-3 text-sm text-muted-foreground">
            Booking đang được giữ đến {displayDateTime(booking.holdExpiresAt)}.
          </div>
        )}

        <div className="flex flex-wrap justify-end gap-2 border-t pt-4">
          {canPayBooking(booking) && (
            <Button asChild size="sm" className="flex-1">
              <Link href={`/payment/${booking.publicId}`}>
                <CreditCard data-icon="inline-start" />
                Thanh toán booking
              </Link>
            </Button>
          )}
          <Button asChild type="button" variant="outline" size="sm" className={booking.status === "PENDING" ? "flex-1" : undefined}>
            <Link href={`/profile/bookings/${booking.publicId}`}>
              <Eye data-icon="inline-start" />
              Xem chi tiết
            </Link>
          </Button>
          {booking.status === "PENDING" && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="flex-1 border-destructive/30 text-destructive hover:bg-destructive/10"
              onClick={() => onRequestDeleteBooking(booking)}
            >
              Xóa booking
            </Button>
          )}
          {booking.status === "CONFIRMED" && (
            <Button type="button" variant="outline" size="sm" className="flex-1" onClick={() => onCancel(booking)}>
              Hủy booking
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function InfoBlock({
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
      <Icon className="mt-0.5 text-muted-foreground" />
      <div className="flex flex-col gap-1">
        <span className="text-xs uppercase text-muted-foreground">{label}</span>
        <span className="text-sm font-medium">{value}</span>
      </div>
    </div>
  )
}
