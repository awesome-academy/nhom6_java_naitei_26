import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Badge, getBookingStatusVariant } from "@/components/ui/status-badge"
import { StatCard } from "@/components/ui/stat-card"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  ArrowUpRight,
  ArrowDownRight,
  Users,
  CalendarCheck,
  Bed,
  DollarSign,
  Clock,
  AlertCircle,
} from "lucide-react"

// Mock data - will be replaced with API calls
const stats = [
  {
    label: "Check-in hôm nay",
    value: "12",
    icon: CalendarCheck,
    trend: { value: 8, isPositive: true },
    description: "khách",
  },
  {
    label: "Phòng trống",
    value: "45",
    icon: Bed,
    trend: { value: 3, isPositive: false },
    description: "phòng",
  },
  {
    label: "Đơn đặt chờ xử lý",
    value: "8",
    icon: Clock,
    trend: { value: 2, isPositive: true },
    description: "đơn",
  },
  {
    label: "Doanh thu hôm nay",
    value: "₫45,230,000",
    icon: DollarSign,
    trend: { value: 15, isPositive: true },
    description: "VNĐ",
  },
]

const recentBookings = [
  {
    id: "BK001",
    guest: "Nguyễn Văn A",
    room: "101",
    checkIn: "2026-08-22",
    checkOut: "2026-08-25",
    status: "CONFIRMED",
    total: "₫4,500,000",
  },
  {
    id: "BK002",
    guest: "Trần Thị B",
    room: "201",
    checkIn: "2026-08-22",
    checkOut: "2026-08-24",
    status: "PENDING",
    total: "₫2,800,000",
  },
  {
    id: "BK003",
    guest: "Lê Minh C",
    room: "305",
    checkIn: "2026-08-21",
    checkOut: "2026-08-23",
    status: "CHECKED_IN",
    total: "₫3,200,000",
  },
  {
    id: "BK004",
    guest: "Phạm Thị D",
    room: "102",
    checkIn: "2026-08-20",
    checkOut: "2026-08-22",
    status: "CHECKED_OUT",
    total: "₫3,600,000",
  },
  {
    id: "BK005",
    guest: "Hoàng Văn E",
    room: "401",
    checkIn: "2026-08-23",
    checkOut: "2026-08-26",
    status: "CONFIRMED",
    total: "₫5,400,000",
  },
]

const roomStatusData = [
  { floor: "Tầng 1", available: 8, occupied: 4, dirty: 2, maintenance: 1 },
  { floor: "Tầng 2", available: 6, occupied: 6, dirty: 1, maintenance: 0 },
  { floor: "Tầng 3", available: 7, occupied: 5, dirty: 0, maintenance: 2 },
  { floor: "Tầng 4", available: 10, occupied: 3, dirty: 1, maintenance: 1 },
]

export default function AdminDashboard() {
  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--foreground)]">Dashboard</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Chào mừng bạn quay trở lại! Đây là tổng quan hệ thống.
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" asChild>
            <Link href="/admin/bookings/new">
              <CalendarCheck className="mr-2 h-4 w-4" />
              Đặt phòng mới
            </Link>
          </Button>
          <Button asChild>
            <Link href="/admin/rooms">
              <Bed className="mr-2 h-4 w-4" />
              Quản lý phòng
            </Link>
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat, i) => (
          <StatCard
            key={i}
            label={stat.label}
            value={stat.value}
            description={stat.description}
            trend={stat.trend}
            icon={<stat.icon className="h-5 w-5" />}
          />
        ))}
      </div>

      {/* Content Grid */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Recent Bookings */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <div className="space-y-1">
              <CardTitle className="text-base font-semibold">Đơn đặt gần đây</CardTitle>
              <CardDescription>5 đơn đặt mới nhất</CardDescription>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link href="/admin/bookings">
                Xem tất cả
                <ArrowUpRight className="ml-1 h-4 w-4" />
              </Link>
            </Button>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {recentBookings.map((booking) => (
                <div
                  key={booking.id}
                  className="flex items-center justify-between rounded-lg border border-[var(--border)] p-3 hover:bg-[var(--muted)]/30 transition-colors"
                >
                  <div className="flex items-center gap-4">
                    <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--muted)] text-sm font-medium">
                      {booking.guest.split(" ").pop()?.charAt(0)}
                    </div>
                    <div>
                      <p className="font-medium text-[var(--foreground)]">{booking.guest}</p>
                      <p className="text-xs text-[var(--muted-foreground)]">
                        Phòng {booking.room} · {booking.checkIn} → {booking.checkOut}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <Badge variant={getBookingStatusVariant(booking.status)}>
                      {booking.status === "CHECKED_IN" ? "Đã nhận phòng" :
                       booking.status === "CHECKED_OUT" ? "Đã trả phòng" :
                       booking.status === "CONFIRMED" ? "Đã xác nhận" :
                       booking.status === "PENDING" ? "Chờ xử lý" : booking.status}
                    </Badge>
                    <p className="mt-1 text-sm font-medium text-[var(--foreground)]">
                      {booking.total}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Room Availability */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <div className="space-y-1">
              <CardTitle className="text-base font-semibold">Tình trạng phòng</CardTitle>
              <CardDescription>Theo tầng</CardDescription>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link href="/admin/rooms">
                Chi tiết
                <ArrowUpRight className="ml-1 h-4 w-4" />
              </Link>
            </Button>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {roomStatusData.map((floor) => (
                <div key={floor.floor} className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-[var(--foreground)]">{floor.floor}</span>
                    <span className="text-[var(--muted-foreground)]">
                      {floor.available + floor.occupied + floor.dirty + floor.maintenance} phòng
                    </span>
                  </div>
                  <div className="flex h-2 rounded-full overflow-hidden gap-1">
                    <div
                      className="bg-green-500 rounded-full"
                      style={{ width: `${(floor.available / 15) * 100}%` }}
                      title={`Trống: ${floor.available}`}
                    />
                    <div
                      className="bg-blue-500 rounded-full"
                      style={{ width: `${(floor.occupied / 15) * 100}%` }}
                      title={`Đã đặt: ${floor.occupied}`}
                    />
                    <div
                      className="bg-orange-400 rounded-full"
                      style={{ width: `${(floor.dirty / 15) * 100}%` }}
                      title={`Cần dọn: ${floor.dirty}`}
                    />
                    <div
                      className="bg-red-500 rounded-full"
                      style={{ width: `${(floor.maintenance / 15) * 100}%` }}
                      title={`Bảo trì: ${floor.maintenance}`}
                    />
                  </div>
                  <div className="flex gap-4 text-xs text-[var(--muted-foreground)]">
                    <span className="flex items-center gap-1">
                      <span className="h-2 w-2 rounded-full bg-green-500" /> Trống {floor.available}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="h-2 w-2 rounded-full bg-blue-500" /> Đã đặt {floor.occupied}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="h-2 w-2 rounded-full bg-orange-400" /> Cần dọn {floor.dirty}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="h-2 w-2 rounded-full bg-red-500" /> Bảo trì {floor.maintenance}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base font-semibold">Thao tác nhanh</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Button variant="outline" className="h-auto flex-col gap-2 py-4" asChild>
              <Link href="/admin/bookings/new">
                <CalendarCheck className="h-5 w-5" />
                <span className="font-medium">Check-in nhanh</span>
              </Link>
            </Button>
            <Button variant="outline" className="h-auto flex-col gap-2 py-4" asChild>
              <Link href="/admin/bookings/new">
                <Users className="h-5 w-5" />
                <span className="font-medium">Tạo đơn mới</span>
              </Link>
            </Button>
            <Button variant="outline" className="h-auto flex-col gap-2 py-4" asChild>
              <Link href="/admin/rooms?status=dirty">
                <AlertCircle className="h-5 w-5" />
                <span className="font-medium">Danh sách cần dọn</span>
              </Link>
            </Button>
            <Button variant="outline" className="h-auto flex-col gap-2 py-4" asChild>
              <Link href="/admin/reports">
                <DollarSign className="h-5 w-5" />
                <span className="font-medium">Xem báo cáo</span>
              </Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
