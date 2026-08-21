"use client"

import { useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/Button"
import { Badge, getBookingStatusVariant, getPaymentStatusVariant } from "@/components/ui/status-badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card"
import { Input } from "@/components/ui/Input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/Select"
import {
  Search,
  Calendar,
  MapPin,
  Clock,
  Star,
  Eye,
  XCircle,
  MessageSquare,
} from "lucide-react"

// Mock bookings data
const bookings = [
  {
    id: "BK-2026-0001",
    hotel: "TripStay Grand Hotel",
    roomType: "Deluxe Ocean View",
    room: "301",
    address: "123 Đường Biển, Vũng Tàu",
    checkIn: "2026-08-20",
    checkOut: "2026-08-23",
    nights: 3,
    guests: 2,
    status: "COMPLETED",
    paymentStatus: "PAID",
    total: "₫4,500,000",
    paid: "₫4,500,000",
    hasReview: true,
  },
  {
    id: "BK-2026-0002",
    hotel: "TripStay City Center",
    roomType: "Standard Room",
    room: "205",
    address: "456 Đường Trung Tâm, TP.HCM",
    checkIn: "2026-09-10",
    checkOut: "2026-09-12",
    nights: 2,
    guests: 1,
    status: "CONFIRMED",
    paymentStatus: "PAID",
    total: "₫2,200,000",
    paid: "₫2,200,000",
    hasReview: false,
  },
  {
    id: "BK-2026-0003",
    hotel: "TripStay Mountain Resort",
    roomType: "VIP Suite",
    room: "401",
    address: "789 Đường Núi, Đà Lạt",
    checkIn: "2026-08-25",
    checkOut: "2026-08-28",
    nights: 3,
    guests: 2,
    status: "UPCOMING",
    paymentStatus: "PARTIALLY_PAID",
    total: "₫7,500,000",
    paid: "₫2,500,000",
    hasReview: false,
  },
  {
    id: "BK-2026-0004",
    hotel: "TripStay Beach Resort",
    roomType: "Beach Bungalow",
    room: "B12",
    address: "321 Đường Biển, Phú Quốc",
    checkIn: "2026-07-15",
    checkOut: "2026-07-18",
    nights: 3,
    guests: 2,
    status: "COMPLETED",
    paymentStatus: "PAID",
    total: "₫5,400,000",
    paid: "₫5,400,000",
    hasReview: true,
  },
  {
    id: "BK-2026-0005",
    hotel: "TripStay Airport Hotel",
    roomType: "Standard Room",
    room: "118",
    address: "Tân Sơn Nhất, TP.HCM",
    checkIn: "2026-06-01",
    checkOut: "2026-06-02",
    nights: 1,
    guests: 1,
    status: "CANCELLED",
    paymentStatus: "REFUNDED",
    total: "₫800,000",
    paid: "₫800,000",
    hasReview: false,
  },
]

const statusConfig: Record<string, { label: string; color: string }> = {
  UPCOMING: { label: "Sắp tới", color: "text-blue-600" },
  COMPLETED: { label: "Đã hoàn thành", color: "text-green-600" },
  CANCELLED: { label: "Đã hủy", color: "text-red-600" },
  ONGOING: { label: "Đang ở", color: "text-purple-600" },
}

export default function ProfileBookingsPage() {
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("all")

  const filteredBookings = bookings.filter((booking) => {
    const matchesSearch =
      booking.id.toLowerCase().includes(search.toLowerCase()) ||
      booking.hotel.toLowerCase().includes(search.toLowerCase())
    const matchesStatus =
      statusFilter === "all" ||
      (statusFilter === "upcoming" && booking.status === "UPCOMING") ||
      (statusFilter === "completed" && booking.status === "COMPLETED") ||
      (statusFilter === "cancelled" && booking.status === "CANCELLED")
    return matchesSearch && matchesStatus
  })

  const stats = {
    total: bookings.length,
    upcoming: bookings.filter((b) => b.status === "UPCOMING").length,
    completed: bookings.filter((b) => b.status === "COMPLETED").length,
    cancelled: bookings.filter((b) => b.status === "CANCELLED").length,
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold text-[var(--foreground)]">Đơn đặt phòng</h1>
        <p className="text-sm text-[var(--muted-foreground)]">
          Theo dõi lịch sử và trạng thái đơn đặt của bạn
        </p>
      </div>

      {/* Stats */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[var(--accent)]/10">
                <Calendar className="h-5 w-5 text-[var(--accent)]" />
              </div>
              <div>
                <p className="text-2xl font-bold">{stats.total}</p>
                <p className="text-sm text-[var(--muted-foreground)]">Tổng đơn</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100">
                <Clock className="h-5 w-5 text-blue-600" />
              </div>
              <div>
                <p className="text-2xl font-bold">{stats.upcoming}</p>
                <p className="text-sm text-[var(--muted-foreground)]">Sắp tới</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100">
                <Calendar className="h-5 w-5 text-green-600" />
              </div>
              <div>
                <p className="text-2xl font-bold">{stats.completed}</p>
                <p className="text-sm text-[var(--muted-foreground)]">Đã hoàn thành</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-4">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-red-100">
                <XCircle className="h-5 w-5 text-red-600" />
              </div>
              <div>
                <p className="text-2xl font-bold">{stats.cancelled}</p>
                <p className="text-sm text-[var(--muted-foreground)]">Đã hủy</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <Input
            placeholder="Tìm theo mã đặt phòng, khách sạn..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Trạng thái" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tất cả</SelectItem>
            <SelectItem value="upcoming">Sắp tới</SelectItem>
            <SelectItem value="completed">Đã hoàn thành</SelectItem>
            <SelectItem value="cancelled">Đã hủy</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Bookings List */}
      <div className="space-y-4">
        {filteredBookings.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <Calendar className="h-12 w-12 text-[var(--muted-foreground)]" />
              <p className="mt-4 text-lg font-medium text-[var(--foreground)]">
                Không tìm thấy đơn đặt phòng
              </p>
              <p className="mt-1 text-sm text-[var(--muted-foreground)]">
                Hãy thử thay đổi bộ lọc hoặc tìm kiếm
              </p>
            </CardContent>
          </Card>
        ) : (
          filteredBookings.map((booking) => (
            <Card
              key={booking.id}
              className="hover:border-[var(--accent)]/50 transition-colors"
            >
              <CardContent className="p-6">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  {/* Booking Info */}
                  <div className="flex-1 space-y-4">
                    <div className="flex items-start justify-between">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm text-[var(--muted-foreground)]">
                            {booking.id}
                          </span>
                          <Badge
                            variant={
                              booking.status === "COMPLETED"
                                ? "success"
                                : booking.status === "CANCELLED"
                                ? "destructive"
                                : "default"
                            }
                          >
                            {statusConfig[booking.status]?.label || booking.status}
                          </Badge>
                        </div>
                        <h3 className="mt-1 text-lg font-semibold text-[var(--foreground)]">
                          {booking.hotel}
                        </h3>
                      </div>
                      <div className="text-right">
                        <p className="text-lg font-bold text-[var(--foreground)]">
                          {booking.total}
                        </p>
                        <p className="text-sm text-[var(--muted-foreground)]">
                          {booking.paymentStatus === "PAID"
                            ? "Đã thanh toán"
                            : booking.paymentStatus === "PARTIALLY_PAID"
                            ? `Đã trả ${booking.paid}`
                            : booking.paymentStatus === "REFUNDED"
                            ? "Đã hoàn tiền"
                            : "Chưa thanh toán"}
                        </p>
                      </div>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                      <div className="flex items-center gap-2 text-sm">
                        <MapPin className="h-4 w-4 text-[var(--muted-foreground)]" />
                        <span className="text-[var(--muted-foreground)]">{booking.address}</span>
                      </div>
                      <div className="flex items-center gap-2 text-sm">
                        <Calendar className="h-4 w-4 text-[var(--muted-foreground)]" />
                        <span>
                          {booking.checkIn} → {booking.checkOut}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 text-sm">
                        <Clock className="h-4 w-4 text-[var(--muted-foreground)]" />
                        <span>{booking.nights} đêm · {booking.guests} khách</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="rounded-full bg-[var(--muted)] px-3 py-1 text-sm">
                        {booking.roomType}
                      </span>
                      <span className="rounded-full bg-[var(--muted)] px-3 py-1 text-sm">
                        Phòng {booking.room}
                      </span>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex flex-col gap-2 lg:min-w-[140px]">
                    <Button variant="outline" size="sm" className="w-full">
                      <Eye className="mr-2 h-4 w-4" />
                      Chi tiết
                    </Button>
                    {booking.status === "COMPLETED" && !booking.hasReview && (
                      <Button variant="outline" size="sm" className="w-full">
                        <Star className="mr-2 h-4 w-4" />
                        Viết đánh giá
                      </Button>
                    )}
                    {booking.status === "UPCOMING" && (
                      <Button variant="destructive" size="sm" className="w-full">
                        <XCircle className="mr-2 h-4 w-4" />
                        Hủy đặt phòng
                      </Button>
                    )}
                    {booking.hasReview && (
                      <Button variant="ghost" size="sm" className="w-full">
                        <MessageSquare className="mr-2 h-4 w-4" />
                        Xem đánh giá
                      </Button>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  )
}
