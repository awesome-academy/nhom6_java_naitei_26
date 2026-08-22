"use client"

import { useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Badge, getBookingStatusVariant, getPaymentStatusVariant } from "@/components/ui/badge"
import { DataTable } from "@/components/ui/dataTable"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Calendar, Search, Plus, Filter, Eye } from "lucide-react"

// Mock data
const bookings = [
  {
    id: "BK-2026-0001",
    guest: "Nguyễn Văn A",
    email: "nguyenvana@email.com",
    phone: "090 123 4567",
    room: "101",
    type: "Standard",
    checkIn: "2026-08-22",
    checkOut: "2026-08-25",
    nights: 3,
    status: "CONFIRMED",
    paymentStatus: "PAID",
    total: "₫4,500,000",
    source: "Website",
  },
  {
    id: "BK-2026-0002",
    guest: "Trần Thị B",
    email: "tranthib@email.com",
    phone: "091 234 5678",
    room: "201",
    type: "Deluxe",
    checkIn: "2026-08-22",
    checkOut: "2026-08-24",
    nights: 2,
    status: "PENDING",
    paymentStatus: "UNPAID",
    total: "₫2,800,000",
    source: "Walk-in",
  },
  {
    id: "BK-2026-0003",
    guest: "Lê Minh C",
    email: "leminhc@email.com",
    phone: "092 345 6789",
    room: "305",
    type: "Suite",
    checkIn: "2026-08-21",
    checkOut: "2026-08-23",
    nights: 2,
    status: "CHECKED_IN",
    paymentStatus: "PAID",
    total: "₫3,200,000",
    source: "Booking.com",
  },
  {
    id: "BK-2026-0004",
    guest: "Phạm Thị D",
    email: "phamthid@email.com",
    phone: "093 456 7890",
    room: "102",
    type: "Standard",
    checkIn: "2026-08-20",
    checkOut: "2026-08-22",
    nights: 2,
    status: "CHECKED_OUT",
    paymentStatus: "PAID",
    total: "₫3,600,000",
    source: "Website",
  },
  {
    id: "BK-2026-0005",
    guest: "Hoàng Văn E",
    email: "hoangvane@email.com",
    phone: "094 567 8901",
    room: "401",
    type: "VIP Suite",
    checkIn: "2026-08-23",
    checkOut: "2026-08-26",
    nights: 3,
    status: "CONFIRMED",
    paymentStatus: "PARTIALLY_PAID",
    total: "₫5,400,000",
    source: "Agoda",
  },
  {
    id: "BK-2026-0006",
    guest: "Vũ Thị F",
    email: "vuthif@email.com",
    phone: "095 678 9012",
    room: "103",
    type: "Standard",
    checkIn: "2026-08-19",
    checkOut: "2026-08-20",
    nights: 1,
    status: "CANCELLED",
    paymentStatus: "REFUNDED",
    total: "₫1,500,000",
    source: "Phone",
  },
]

const statusLabels: Record<string, string> = {
  PENDING: "Chờ xử lý",
  CONFIRMED: "Đã xác nhận",
  CHECKED_IN: "Đã nhận phòng",
  CHECKED_OUT: "Đã trả phòng",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
}

const paymentLabels: Record<string, string> = {
  UNPAID: "Chưa thanh toán",
  PARTIALLY_PAID: "Thanh toán một phần",
  PAID: "Đã thanh toán",
  REFUNDED: "Đã hoàn tiền",
}

const columns = [
  {
    key: "id",
    header: "Mã đặt phòng",
    render: (row: typeof bookings[0]) => (
      <span className="font-mono text-sm">{row.id}</span>
    ),
  },
  {
    key: "guest",
    header: "Khách hàng",
    render: (row: typeof bookings[0]) => (
      <div>
        <p className="font-medium">{row.guest}</p>
        <p className="text-xs text-[var(--muted-foreground)]">{row.email}</p>
      </div>
    ),
  },
  {
    key: "room",
    header: "Phòng",
    render: (row: typeof bookings[0]) => (
      <div>
        <p className="font-medium">{row.room}</p>
        <p className="text-xs text-[var(--muted-foreground)]">{row.type}</p>
      </div>
    ),
  },
  {
    key: "dates",
    header: "Ngày",
    render: (row: typeof bookings[0]) => (
      <div>
        <p className="text-sm">{row.checkIn} → {row.checkOut}</p>
        <p className="text-xs text-[var(--muted-foreground)]">{row.nights} đêm</p>
      </div>
    ),
  },
  {
    key: "status",
    header: "Trạng thái",
    render: (row: typeof bookings[0]) => (
      <Badge variant={getBookingStatusVariant(row.status)}>
        {statusLabels[row.status] || row.status}
      </Badge>
    ),
  },
  {
    key: "payment",
    header: "Thanh toán",
    render: (row: typeof bookings[0]) => (
      <Badge variant={getPaymentStatusVariant(row.paymentStatus)}>
        {paymentLabels[row.paymentStatus] || row.paymentStatus}
      </Badge>
    ),
  },
  {
    key: "total",
    header: "Tổng tiền",
    className: "text-right font-medium",
  },
]

export default function AdminBookingsPage() {
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("all")
  const [dateFilter, setDateFilter] = useState("all")

  const filteredBookings = bookings.filter((booking) => {
    const matchesSearch =
      booking.id.toLowerCase().includes(search.toLowerCase()) ||
      booking.guest.toLowerCase().includes(search.toLowerCase()) ||
      booking.email.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = statusFilter === "all" || booking.status === statusFilter
    return matchesSearch && matchesStatus
  })

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--foreground)]">Quản lý đặt phòng</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Theo dõi và quản lý tất cả đơn đặt phòng
          </p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          Tạo đơn mới
        </Button>
      </div>

      {/* Stats */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        {[
          { label: "Tổng đơn", value: "156", color: "text-[var(--foreground)]" },
          { label: "Chờ xử lý", value: "8", color: "text-yellow-600" },
          { label: "Đã xác nhận", value: "45", color: "text-blue-600" },
          { label: "Đang ở", value: "23", color: "text-green-600" },
          { label: "Đã hoàn thành", value: "78", color: "text-[var(--muted-foreground)]" },
        ].map((stat) => (
          <Card key={stat.label}>
            <CardContent className="pt-6">
              <div className="text-2xl font-bold">{stat.value}</div>
              <p className={`text-sm ${stat.color}`}>{stat.label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <Input
            placeholder="Tìm theo mã, tên, email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]">
            <Filter className="mr-2 h-4 w-4" />
            <SelectValue placeholder="Trạng thái" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tất cả trạng thái</SelectItem>
            <SelectItem value="PENDING">Chờ xử lý</SelectItem>
            <SelectItem value="CONFIRMED">Đã xác nhận</SelectItem>
            <SelectItem value="CHECKED_IN">Đã nhận phòng</SelectItem>
            <SelectItem value="CHECKED_OUT">Đã trả phòng</SelectItem>
            <SelectItem value="CANCELLED">Đã hủy</SelectItem>
          </SelectContent>
        </Select>
        <Select value={dateFilter} onValueChange={setDateFilter}>
          <SelectTrigger className="w-[180px]">
            <Calendar className="mr-2 h-4 w-4" />
            <SelectValue placeholder="Ngày" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tất cả ngày</SelectItem>
            <SelectItem value="today">Hôm nay</SelectItem>
            <SelectItem value="tomorrow">Ngày mai</SelectItem>
            <SelectItem value="this_week">Tuần này</SelectItem>
            <SelectItem value="this_month">Tháng này</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Bookings Table */}
      <DataTable
        columns={columns}
        data={filteredBookings}
        keyExtractor={(row) => row.id}
        emptyMessage="Không tìm thấy đơn đặt phòng nào"
        onRowClick={(row) => console.log("View booking:", row.id)}
        actions={[
          {
            label: "Chi tiết",
            onClick: (row) => console.log("View:", row.id),
          },
        ]}
      />
    </div>
  )
}
