"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import Link from "next/link"
import { ArrowLeft, CalendarDays, Loader2, Mail, MapPin, Phone, UserRound, UserRoundCheck, UserRoundX } from "lucide-react"
import { toast } from "sonner"

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge, getBookingStatusVariant, getPaymentStatusVariant, getUserStatusVariant } from "@/components/ui/badge"
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
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import { uploadCustomerAvatar } from "@/lib/api/avatar"
import { getCustomer, getCustomerBookings, updateCustomerStatus } from "@/lib/api/admin-customers"
import type { CustomerAccountStatus, CustomerBooking, CustomerDetailResponse } from "@/types/admin-customer"

const statusLabels: Record<string, string> = {
  ACTIVE: "Đang hoạt động",
  DEACTIVATED: "Đã vô hiệu hóa",
  PENDING_VERIFICATION: "Chờ xác thực",
  SUSPENDED: "Đang tạm khóa",
}

const bookingStatusLabels: Record<string, string> = {
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

function formatDate(value: string | null | undefined) {
  if (!value) return "—"
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(value))
}

function formatStayDate(value: string | null) {
  if (!value) return "Chưa có ngày"
  return formatDate(`${value}T00:00:00`)
}

function formatMoney(value: number | string, currency: string) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(Number(value) || 0)
}

function getErrorMessage(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : "Không thể tải thông tin khách hàng. Vui lòng thử lại."
}

function InfoItem({ icon: Icon, label, value }: { icon: typeof Mail; label: string; value: string }) {
  return (
    <div className="flex items-start gap-3">
      <Icon className="mt-0.5 text-muted-foreground" />
      <div className="flex min-w-0 flex-col gap-1">
        <span className="text-xs text-muted-foreground">{label}</span>
        <span className="break-words text-sm font-medium">{value}</span>
      </div>
    </div>
  )
}

export function CustomerDetail({ publicId }: { publicId: string }) {
  const [detail, setDetail] = useState<CustomerDetailResponse | null>(null)
  const [bookings, setBookings] = useState<CustomerBooking[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isUpdating, setIsUpdating] = useState(false)
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false)
  const [confirmDeactivate, setConfirmDeactivate] = useState(false)
  const avatarInputRef = useRef<HTMLInputElement>(null)

  const loadDetail = useCallback(async () => {
    setIsLoading(true)
    setLoadError(null)
    try {
      const customerDetail = await getCustomer(publicId)
      setDetail(customerDetail)
      try {
        setBookings(await getCustomerBookings(publicId))
      } catch (error) {
        setBookings([])
        toast.error(
          error instanceof Error && error.message
            ? error.message
            : "Không thể tải booking, tạm thời hiển thị 0 booking."
        )
      }
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }, [publicId])

  useEffect(() => {
    const timer = window.setTimeout(() => void loadDetail(), 0)
    return () => window.clearTimeout(timer)
  }, [loadDetail])

  async function changeStatus(status: CustomerAccountStatus) {
    if (!detail) return
    setIsUpdating(true)
    try {
      const account = await updateCustomerStatus(publicId, status)
      setDetail((current) => current ? { ...current, account } : current)
      setConfirmDeactivate(false)
      toast.success(status === "ACTIVE" ? "Đã kích hoạt lại tài khoản" : "Đã vô hiệu hóa tài khoản")
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsUpdating(false)
    }
  }

  async function onAvatarSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ""
    if (!file) return
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Avatar chỉ hỗ trợ JPG, PNG hoặc WebP")
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.error("Avatar không được vượt quá 10 MB")
      return
    }
    setIsUploadingAvatar(true)
    try {
      const response = await uploadCustomerAvatar(publicId, file)
      setDetail((current) => current ? {
        ...current,
        account: { ...current.account, avatarUrl: response.avatarUrl },
        profile: current.profile ? { ...current.profile, avatarUrl: response.avatarUrl } : current.profile,
      } : current)
      toast.success("Đã cập nhật avatar khách hàng")
    } catch (error) {
      console.error("Failed to upload customer avatar", error)
      toast.error("Không thể cập nhật avatar khách hàng")
    } finally {
      setIsUploadingAvatar(false)
    }
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-10 w-44" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-80 w-full" />
      </div>
    )
  }

  if (loadError || !detail) {
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-sm text-destructive">{loadError ?? "Không tìm thấy khách hàng"}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void loadDetail()}>Thử lại</Button>
          <Button asChild><Link href="/manager/guests">Quay lại danh sách</Link></Button>
        </div>
      </div>
    )
  }

  const { account, profile } = detail
  const nextStatus: CustomerAccountStatus = account.status === "DEACTIVATED" ? "ACTIVE" : "DEACTIVATED"

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <Button asChild variant="ghost" className="w-fit px-0">
          <Link href="/manager/guests">
            <ArrowLeft data-icon="inline-start" />
            Quay lại danh sách
          </Link>
        </Button>
        <div className="flex gap-2">
          {account.status === "ACTIVE" && (
            <Button variant="destructive" onClick={() => setConfirmDeactivate(true)} disabled={isUpdating}>
              <UserRoundX data-icon="inline-start" />
              Vô hiệu hóa
            </Button>
          )}
          {account.status === "DEACTIVATED" && (
            <Button onClick={() => void changeStatus(nextStatus)} disabled={isUpdating}>
              {isUpdating ? <Loader2 className="animate-spin" /> : <UserRoundCheck data-icon="inline-start" />}
              Kích hoạt lại
            </Button>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
            <div className="flex items-center gap-4">
              <Avatar className="size-14">
                {account.avatarUrl && <AvatarImage src={account.avatarUrl} alt={account.fullName} />}
                <AvatarFallback className="bg-muted text-muted-foreground"><UserRound /></AvatarFallback>
              </Avatar>
              <div className="flex flex-col gap-2">
                <CardTitle>{account.fullName}</CardTitle>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant={getUserStatusVariant(account.status)}>{statusLabels[account.status]}</Badge>
                  <span className="text-sm text-muted-foreground">Tham gia {formatDate(account.createdAt)}</span>
                </div>
                <Input
                  ref={avatarInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                  onChange={onAvatarSelected}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="w-fit"
                  disabled={isUploadingAvatar}
                  onClick={() => avatarInputRef.current?.click()}
                >
                  {isUploadingAvatar ? "Đang tải lên..." : "Đổi avatar"}
                </Button>
              </div>
            </div>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-5">
          <div className="grid gap-5 md:grid-cols-3">
            <InfoItem icon={Mail} label="Email" value={account.email} />
            <InfoItem icon={Phone} label="Số điện thoại" value={account.phone ?? "Chưa cập nhật"} />
            <InfoItem icon={CalendarDays} label="Ngày đăng ký" value={formatDate(account.createdAt)} />
          </div>
          <Separator />
          <div className="grid gap-5 md:grid-cols-3">
            <InfoItem icon={MapPin} label="Địa chỉ" value={profile?.addressLine ?? "Chưa cập nhật"} />
            <InfoItem icon={MapPin} label="Tỉnh / thành phố" value={profile?.province ?? "Chưa cập nhật"} />
            <InfoItem icon={UserRound} label="Giới tính" value={profile?.gender ?? "Chưa cập nhật"} />
            <InfoItem icon={CalendarDays} label="Ngày sinh" value={formatDate(profile?.dateOfBirth)} />
            <InfoItem icon={MapPin} label="Quốc tịch" value={profile?.nationality ?? "Chưa cập nhật"} />
            <InfoItem icon={MapPin} label="Quốc gia" value={profile?.country ?? "Chưa cập nhật"} />
            <InfoItem icon={UserRound} label="Điểm tích lũy" value={String(profile?.loyaltyPoints ?? 0)} />
            <InfoItem icon={CalendarDays} label="Tổng lượt lưu trú" value={String(profile?.totalStays ?? 0)} />
            <InfoItem icon={UserRound} label="Ghi chú" value={profile?.notes ?? "Không có"} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Lịch sử booking</CardTitle>
          <CardDescription>Danh sách chỉ đọc, sắp xếp từ mới nhất.</CardDescription>
        </CardHeader>
        <CardContent>
          {bookings.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">Khách hàng chưa có booking.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-sm">
                <thead>
                  <tr className="border-b text-left text-xs uppercase text-muted-foreground">
                    <th className="px-3 py-3">Mã booking</th>
                    <th className="px-3 py-3">Ngày lưu trú</th>
                    <th className="px-3 py-3">Số đêm</th>
                    <th className="px-3 py-3">Số phòng</th>
                    <th className="px-3 py-3">Tổng khách</th>
                    <th className="px-3 py-3">Tổng tiền</th>
                    <th className="px-3 py-3">Trạng thái</th>
                    <th className="px-3 py-3">Payment</th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.map((booking) => (
                    <tr key={booking.bookingCode} className="border-b last:border-0">
                      <td className="px-3 py-4 font-medium">{booking.bookingCode}</td>
                      <td className="px-3 py-4">
                        {formatStayDate(booking.checkInDate)} - {formatStayDate(booking.checkOutDate)}
                      </td>
                      <td className="px-3 py-4">{booking.nights}</td>
                      <td className="px-3 py-4">{booking.rooms}</td>
                      <td className="px-3 py-4">{booking.guests}</td>
                      <td className="px-3 py-4 font-medium">{formatMoney(booking.totalAmount, booking.currency)}</td>
                      <td className="px-3 py-4">
                        <Badge variant={getBookingStatusVariant(booking.status)}>
                          {bookingStatusLabels[booking.status] ?? booking.status}
                        </Badge>
                      </td>
                      <td className="px-3 py-4">
                        <Badge variant={getPaymentStatusVariant(booking.paymentStatus)}>
                          {paymentStatusLabels[booking.paymentStatus] ?? booking.paymentStatus}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={confirmDeactivate} onOpenChange={(open) => !open && !isUpdating && setConfirmDeactivate(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Vô hiệu hóa tài khoản?</DialogTitle>
            <DialogDescription>
              Tài khoản sẽ bị chặn đăng nhập và API customer. Booking, profile và lịch sử vẫn được giữ nguyên.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" disabled={isUpdating} onClick={() => setConfirmDeactivate(false)}>Hủy</Button>
            <Button variant="destructive" disabled={isUpdating} onClick={() => void changeStatus("DEACTIVATED")}>
              {isUpdating ? <Loader2 className="animate-spin" /> : <UserRoundX />}
              Vô hiệu hóa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
