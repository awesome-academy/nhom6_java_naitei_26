"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { toast } from "sonner"
import {
  ArrowDownToLine,
  ArrowUpFromLine,
  BedDouble,
  CalendarCheck,
  ChevronRight,
  CircleDollarSign,
  Loader2,
  RefreshCw,
  UserPlus,
} from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getDashboardOverview } from "@/lib/api/dashboard"
import { useAuth } from "@/lib/auth-context"
import type { DashboardOverview, DashboardStayItem } from "@/types/dashboard"

const DATE_FORMATTER = new Intl.DateTimeFormat("vi-VN", {
  weekday: "short",
  day: "2-digit",
  month: "2-digit",
})

const MONEY_FORMATTER = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
})

function localDateString(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function formatDate(value: string) {
  return DATE_FORMATTER.format(new Date(`${value}T00:00:00`))
}

function formatMoney(value: number, currency = "VND") {
  if (currency === "VND") return MONEY_FORMATTER.format(value)
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value)
}

function formatChange(value: number | null) {
  if (value === null) return "Chưa có dữ liệu tháng trước"
  if (value === 0) return "Không thay đổi so với tháng trước"
  return `${value > 0 ? "+" : ""}${value.toFixed(2)}% so với tháng trước`
}

export function DashboardOverviewPage({ portal = "/manager" }: { portal?: "/manager" }) {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [overview, setOverview] = useState<DashboardOverview | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const selectedDate = useMemo(() => localDateString(), [])

  const loadDashboard = useCallback(async () => {
    if (!isAuthenticated) return
    setIsLoading(true)
    setError(null)
    try {
      setOverview(await getDashboardOverview(selectedDate))
    } catch (loadError) {
      const message = loadError instanceof Error
        ? loadError.message
        : "Không thể tải dữ liệu dashboard."
      setError(message)
      toast.error(message)
    } finally {
      setIsLoading(false)
    }
  }, [isAuthenticated, selectedDate])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadDashboard()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [loadDashboard])

  if (isAuthLoading || !isAuthenticated) {
    return <DashboardLoading />
  }

  return (
    <main className="flex flex-col gap-6 p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            Tổng quan vận hành ngày {overview ? formatDate(overview.date) : formatDate(selectedDate)}.
          </p>
        </div>
        <Button variant="outline" onClick={() => void loadDashboard()} disabled={isLoading}>
          <RefreshCw data-icon="inline-start" className={isLoading ? "animate-spin" : undefined} />
          Làm mới
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>Không thể tải dashboard</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {isLoading && !overview ? (
        <DashboardLoading />
      ) : overview ? (
        <>
          <SummaryCards overview={overview} />
          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(20rem,0.75fr)]">
            <ArrivalsCard arrivals={overview.arrivals} portal={portal} />
            <OccupancyCard overview={overview} portal={portal} />
          </div>
          <QuickActions portal={portal} />
        </>
      ) : null}
    </main>
  )
}

function SummaryCards({ overview }: { overview: DashboardOverview }) {
  return (
    <div className="grid gap-4 md:grid-cols-3">
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4 pb-2">
          <div>
            <CardDescription>Booking hôm nay</CardDescription>
            <CardTitle className="mt-2 text-3xl">
              {overview.bookingSummary.arrivalsCount + overview.bookingSummary.departuresCount}
            </CardTitle>
          </div>
          <div className="rounded-lg bg-primary/10 p-2 text-primary">
            <CalendarCheck className="size-5" />
          </div>
        </CardHeader>
        <CardContent className="flex gap-4 text-sm">
          <span className="flex items-center gap-1 text-emerald-600">
            <ArrowDownToLine className="size-4" /> Đến {overview.bookingSummary.arrivalsCount}
          </span>
          <span className="flex items-center gap-1 text-amber-600">
            <ArrowUpFromLine className="size-4" /> Đi {overview.bookingSummary.departuresCount}
          </span>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4 pb-2">
          <div>
            <CardDescription>Phòng trống</CardDescription>
            <CardTitle className="mt-2 text-3xl">{overview.roomSummary.availableRooms}</CardTitle>
          </div>
          <div className="rounded-lg bg-emerald-500/10 p-2 text-emerald-600">
            <BedDouble className="size-5" />
          </div>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          {overview.roomSummary.occupiedRooms}/{overview.roomSummary.totalRooms} phòng đang được đặt · {overview.roomSummary.occupancyPercent.toFixed(2)}% lấp đầy
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4 pb-2">
          <div>
            <CardDescription>Doanh thu tháng này</CardDescription>
            <CardTitle className="mt-2 text-2xl">
              {formatMoney(overview.revenueSummary.currentMonthRevenue, overview.revenueSummary.currency)}
            </CardTitle>
          </div>
          <div className="rounded-lg bg-blue-500/10 p-2 text-blue-600">
            <CircleDollarSign className="size-5" />
          </div>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          {formatChange(overview.revenueSummary.changePercent)}
        </CardContent>
      </Card>
    </div>
  )
}

function ArrivalsCard({ arrivals, portal }: { arrivals: DashboardStayItem[]; portal: "/manager" }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle>Khách đến hôm nay</CardTitle>
          <CardDescription>Danh sách lấy từ các phòng có trạng thái RESERVED.</CardDescription>
        </div>
        <Badge variant="outline">{arrivals.length} phòng</Badge>
      </CardHeader>
      <CardContent>
        {arrivals.length === 0 ? (
          <div className="flex min-h-32 items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
            Không có khách đến hôm nay.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
                <tr>
                  <th className="px-3 py-3 font-medium">Khách</th>
                  <th className="px-3 py-3 font-medium">Phòng</th>
                  <th className="px-3 py-3 font-medium">Lưu trú</th>
                  <th className="px-3 py-3 text-right font-medium">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {arrivals.map((arrival) => (
                  <tr key={`${arrival.bookingPublicId}-${arrival.roomNumber}`} className="border-b last:border-0">
                    <td className="px-3 py-3">
                      <div className="flex flex-col gap-1">
                        <span className="font-medium">{arrival.contactName}</span>
                        <span className="text-xs text-muted-foreground">{arrival.bookingCode}</span>
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-col gap-1">
                        <span className="font-medium">{arrival.roomNumber}</span>
                        <span className="text-xs text-muted-foreground">{arrival.roomTypeName}</span>
                      </div>
                    </td>
                    <td className="px-3 py-3 text-muted-foreground">
                      {arrival.checkInDate} → {arrival.checkOutDate}
                    </td>
                    <td className="px-3 py-3 text-right">
                      <Button variant="ghost" size="sm" asChild>
                        <Link href={`${portal}/bookings`}>
                          Xem <ChevronRight data-icon="inline-end" />
                        </Link>
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function OccupancyCard({ overview, portal }: { overview: DashboardOverview; portal: "/manager" }) {
  const maxOccupancy = Math.max(...overview.occupancyNext7Days.map((day) => day.occupancyPercent), 1)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Occupancy 7 ngày tới</CardTitle>
        <CardDescription>Phần trăm phòng đã được đặt hoặc đang sử dụng.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex h-48 items-end gap-2 rounded-lg border bg-muted/20 px-3 pb-3 pt-6">
          {overview.occupancyNext7Days.map((day) => {
            const height = Math.max(4, (day.occupancyPercent / maxOccupancy) * 100)
            return (
              <div key={day.date} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-2">
                <span className="text-xs font-medium tabular-nums">{day.occupancyPercent.toFixed(0)}%</span>
                <div className="flex h-full w-full items-end rounded-sm bg-muted">
                  <div
                    className="w-full rounded-sm bg-primary transition-all"
                    style={{ height: `${height}%` }}
                    title={`${day.occupiedRooms} đặt / ${day.totalRooms} phòng`}
                  />
                </div>
                <span className="truncate text-[11px] text-muted-foreground">{formatDate(day.date)}</span>
              </div>
            )
          })}
        </div>
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>Hôm nay còn {overview.roomSummary.availableRooms} phòng trống</span>
          <Link href={`${portal}/rooms`} className="font-medium text-primary hover:underline">Quản lý phòng</Link>
        </div>
      </CardContent>
    </Card>
  )
}

function QuickActions({ portal }: { portal: "/manager" }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Thao tác nhanh</CardTitle>
        <CardDescription>Mở đúng màn hình để tiếp tục xử lý booking.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-3">
        <Button variant="outline" className="justify-start" asChild>
          <Link href={`${portal}/bookings`}>
            <ArrowDownToLine data-icon="inline-start" /> Check-in
          </Link>
        </Button>
        <Button variant="outline" className="justify-start" asChild>
          <Link href={`${portal}/bookings`}>
            <ArrowUpFromLine data-icon="inline-start" /> Check-out
          </Link>
        </Button>
        <Button className="justify-start" asChild>
          <Link href="/booking">
            <UserPlus data-icon="inline-start" /> Tạo booking
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}

function DashboardLoading() {
  return (
    <main className="flex flex-col gap-6 p-6">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-72" />
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => <Skeleton key={index} className="h-36 rounded-xl" />)}
      </div>
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(20rem,0.75fr)]">
        <Skeleton className="h-96 rounded-xl" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
      <Loader2 className="mx-auto size-5 animate-spin text-muted-foreground" />
    </main>
  )
}
