"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { format, subDays } from "date-fns"
import {
  CalendarDays,
  Download,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
} from "lucide-react"
import { RevenueCharts } from "@/components/admin/reports/revenue-charts"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Calendar } from "@/components/ui/calendar"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  getDailyRevenue,
  getMonthlyRevenue,
  getOccupancyMetrics,
  getRevenueByRoomType,
  getRevenueBySource,
} from "@/lib/api/revenue"
import { useAuth } from "@/lib/auth-context"
import { cn } from "@/lib/utils"
import type { RevenueRange, RevenueReportData } from "@/types/revenue"

const ALL_FILTER = "ALL"
const INITIAL_RANGE: RevenueRange = {
  from: format(subDays(new Date(), 29), "yyyy-MM-dd"),
  to: format(new Date(), "yyyy-MM-dd"),
}

interface DateRangeSelection {
  from: Date
  to?: Date
}

export function RevenueReportsPage() {
  const { user, isLoading: isAuthLoading } = useAuth()
  const [appliedRange, setAppliedRange] = useState<RevenueRange>(INITIAL_RANGE)
  const [draftRange, setDraftRange] = useState<DateRangeSelection>(() => toDateRange(INITIAL_RANGE))
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false)
  const [roomTypeFilter, setRoomTypeFilter] = useState(ALL_FILTER)
  const [sourceFilter, setSourceFilter] = useState(ALL_FILTER)
  const [report, setReport] = useState<RevenueReportData | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const requestIdRef = useRef(0)

  const canReadRevenue = user?.permissions.includes("revenue:read") ?? false

  const loadReport = useCallback(async () => {
    const requestId = ++requestIdRef.current
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const [occupancy, daily, monthly, bySource, byRoomType] = await Promise.all([
        getOccupancyMetrics(appliedRange),
        getDailyRevenue(appliedRange),
        getMonthlyRevenue(appliedRange),
        getRevenueBySource(appliedRange),
        getRevenueByRoomType(appliedRange, 100),
      ])
      if (requestId !== requestIdRef.current) return
      setReport({ occupancy, daily, monthly, bySource, byRoomType })
    } catch (error) {
      if (requestId !== requestIdRef.current) return
      setReport(null)
      setErrorMessage(error instanceof Error ? error.message : "Không thể tải dữ liệu báo cáo.")
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false)
      }
    }
  }, [appliedRange])

  useEffect(() => {
    if (!canReadRevenue) return
    const timer = window.setTimeout(() => {
      void loadReport()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [canReadRevenue, loadReport])

  const filteredSources = useMemo(() => {
    if (!report || sourceFilter === ALL_FILTER) return report?.bySource ?? []
    return report.bySource.filter((source) => source.sourceCode === sourceFilter)
  }, [report, sourceFilter])

  const filteredRoomTypes = useMemo(() => {
    if (!report || roomTypeFilter === ALL_FILTER) return report?.byRoomType ?? []
    return report.byRoomType.filter((roomType) => roomType.roomTypeCode === roomTypeFilter)
  }, [report, roomTypeFilter])

  function handleSelectDate(date: Date) {
    setDraftRange((current) => {
      if (current.to || date < current.from) {
        return { from: date }
      }
      return { from: current.from, to: date }
    })
  }

  function handleApplyDateRange() {
    if (!draftRange.to) return
    setAppliedRange({
      from: format(draftRange.from, "yyyy-MM-dd"),
      to: format(draftRange.to, "yyyy-MM-dd"),
    })
    setIsDatePickerOpen(false)
  }

  function handleExportCsv() {
    if (!report) return
    const csv = buildRevenueCsv({
      range: appliedRange,
      report,
      sources: filteredSources,
      roomTypes: filteredRoomTypes.slice(0, 10),
    })
    const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement("a")
    anchor.href = url
    anchor.download = `bao-cao-doanh-thu_${appliedRange.from}_${appliedRange.to}.csv`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  }

  if (isAuthLoading) {
    return <ReportLoadingSkeleton />
  }

  if (!canReadRevenue) {
    return (
      <div className="mx-auto max-w-2xl">
        <Alert variant="destructive">
          <ShieldAlert data-icon="inline-start" />
          <AlertTitle>Bạn chưa có quyền xem báo cáo doanh thu</AlertTitle>
          <AlertDescription>
            Tài khoản cần quyền <code>revenue:read</code>. Hãy liên hệ quản trị viên để được cấp quyền.
          </AlertDescription>
        </Alert>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Báo cáo doanh thu</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Theo dõi doanh thu, hiệu suất phòng và tỷ trọng các kênh booking.
          </p>
        </div>
        <Button onClick={handleExportCsv} disabled={!report || isLoading}>
          <Download data-icon="inline-start" />
          Xuất CSV
        </Button>
      </div>

      <Card>
        <CardContent className="grid gap-4 pt-6 lg:grid-cols-[minmax(16rem,1fr)_minmax(11rem,0.65fr)_minmax(11rem,0.65fr)_auto] lg:items-end">
          <div className="grid gap-2">
            <Label>Khoảng thời gian</Label>
            <Popover open={isDatePickerOpen} onOpenChange={setIsDatePickerOpen}>
              <PopoverTrigger asChild>
                <Button variant="outline" className="justify-start font-normal">
                  <CalendarDays data-icon="inline-start" />
                  {formatDateRange(appliedRange)}
                </Button>
              </PopoverTrigger>
              <PopoverContent align="start" className="w-auto p-0">
                <Calendar selectedRange={draftRange} onSelect={handleSelectDate} />
                <div className="flex items-center justify-between border-t p-3">
                  <span className="text-xs text-muted-foreground">
                    {draftRange.to ? formatDateRange({
                      from: format(draftRange.from, "yyyy-MM-dd"),
                      to: format(draftRange.to, "yyyy-MM-dd"),
                    }) : "Chọn ngày kết thúc"}
                  </span>
                  <Button size="sm" onClick={handleApplyDateRange} disabled={!draftRange.to}>
                    Áp dụng
                  </Button>
                </div>
              </PopoverContent>
            </Popover>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="room-type-filter">Loại phòng</Label>
            <Select value={roomTypeFilter} onValueChange={setRoomTypeFilter}>
              <SelectTrigger id="room-type-filter">
                <SelectValue placeholder="Tất cả loại phòng" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value={ALL_FILTER}>Tất cả loại phòng</SelectItem>
                  {(report?.byRoomType ?? []).map((roomType) => (
                    <SelectItem key={roomType.roomTypeCode} value={roomType.roomTypeCode}>
                      {roomType.roomTypeName}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="booking-source-filter">Nguồn booking</Label>
            <Select value={sourceFilter} onValueChange={setSourceFilter}>
              <SelectTrigger id="booking-source-filter">
                <SelectValue placeholder="Tất cả nguồn" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value={ALL_FILTER}>Tất cả nguồn</SelectItem>
                  {(report?.bySource ?? []).map((source) => (
                    <SelectItem key={source.sourceCode} value={source.sourceCode}>
                      {source.sourceName}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>

          <Button variant="outline" onClick={() => void loadReport()} disabled={isLoading}>
            <RefreshCw data-icon="inline-start" className={cn(isLoading && "animate-spin")} />
            Làm mới
          </Button>
        </CardContent>
      </Card>

      {errorMessage ? (
        <Alert variant="destructive">
          <ShieldAlert data-icon="inline-start" />
          <AlertTitle>Không tải được báo cáo</AlertTitle>
          <AlertDescription>{errorMessage}</AlertDescription>
        </Alert>
      ) : null}

      {isLoading || !report ? (
        <ReportLoadingSkeleton />
      ) : (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Chỉ số doanh thu">
            <MetricCard label="ADR" value={formatCurrency(report.occupancy.adr)} description="Doanh thu trung bình mỗi đêm phòng bán được" />
            <MetricCard label="RevPAR" value={formatCurrency(report.occupancy.revPar)} description="Doanh thu trung bình mỗi đêm phòng sẵn sàng bán" />
            <MetricCard
              label="Tỷ lệ lấp đầy"
              value={formatPercent(report.occupancy.occupancyRatePercent)}
              description={`${report.occupancy.occupiedRoomNights.toLocaleString("vi-VN")} / ${report.occupancy.availableRoomNights.toLocaleString("vi-VN")} đêm phòng`}
            />
          </section>

          <RevenueCharts
            daily={report.daily}
            monthly={report.monthly}
            bySource={filteredSources}
          />

          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div className="space-y-1.5">
                <CardTitle>Top 10 loại phòng theo doanh thu</CardTitle>
                <CardDescription>
                  Doanh thu lấy từ giá từng đêm và tên loại phòng được snapshot khi booking phát sinh.
                </CardDescription>
              </div>
              {roomTypeFilter !== ALL_FILTER ? <Badge variant="secondary">Đang lọc 1 loại phòng</Badge> : null}
            </CardHeader>
            <CardContent className="overflow-x-auto">
              <table className="w-full min-w-[42rem] text-sm">
                <thead className="border-b text-left text-muted-foreground">
                  <tr>
                    <th className="px-3 py-3 font-medium">#</th>
                    <th className="px-3 py-3 font-medium">Loại phòng</th>
                    <th className="px-3 py-3 text-right font-medium">Đêm phòng</th>
                    <th className="px-3 py-3 text-right font-medium">ADR</th>
                    <th className="px-3 py-3 text-right font-medium">Doanh thu</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRoomTypes.slice(0, 10).map((roomType, index) => (
                    <tr key={roomType.roomTypeCode} className="border-b last:border-0 hover:bg-muted/40">
                      <td className="px-3 py-4 text-muted-foreground">{index + 1}</td>
                      <td className="px-3 py-4">
                        <p className="font-medium">{roomType.roomTypeName}</p>
                        <p className="mt-0.5 font-mono text-xs text-muted-foreground">{roomType.roomTypeCode}</p>
                      </td>
                      <td className="px-3 py-4 text-right tabular-nums">{roomType.roomNights.toLocaleString("vi-VN")}</td>
                      <td className="px-3 py-4 text-right font-medium tabular-nums">{formatCurrency(roomType.adr)}</td>
                      <td className="px-3 py-4 text-right font-semibold tabular-nums">{formatCurrency(roomType.revenue)}</td>
                    </tr>
                  ))}
                  {filteredRoomTypes.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-3 py-12 text-center text-muted-foreground">
                        Chưa có doanh thu theo loại phòng trong khoảng ngày này.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <p className="text-xs text-muted-foreground">
            ADR, RevPAR và tỷ lệ lấp đầy phản ánh toàn khách sạn. Bộ lọc nguồn áp dụng cho biểu đồ nguồn; bộ lọc loại phòng áp dụng cho bảng xếp hạng để không phân bổ sai doanh thu của booking nhiều phòng.
          </p>
        </>
      )}
    </div>
  )
}

function MetricCard({ label, value, description }: { label: string; value: string; description: string }) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <TrendingUp className="size-4 text-accent" aria-hidden="true" />
        </div>
        <p className="mt-3 text-2xl font-bold tabular-nums">{value}</p>
        <p className="mt-1 text-xs text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  )
}

function ReportLoadingSkeleton() {
  return (
    <div className="flex flex-col gap-6" aria-label="Đang tải báo cáo">
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-28 w-full" />
      <div className="grid gap-4 md:grid-cols-3">
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <Skeleton className="h-96" />
        <Skeleton className="h-96" />
      </div>
    </div>
  )
}

function toDateRange(range: RevenueRange): DateRangeSelection {
  return {
    from: new Date(`${range.from}T00:00:00`),
    to: new Date(`${range.to}T00:00:00`),
  }
}

function formatDateRange(range: RevenueRange): string {
  return `${format(new Date(`${range.from}T00:00:00`), "dd/MM/yyyy")} – ${format(new Date(`${range.to}T00:00:00`), "dd/MM/yyyy")}`
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value)
}

function formatPercent(value: number): string {
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(value)}%`
}

function csvValue(value: string | number): string {
  const text = String(value).replace(/"/g, '""')
  return `"${text}"`
}

function buildRevenueCsv({
  range,
  report,
  sources,
  roomTypes,
}: {
  range: RevenueRange
  report: RevenueReportData
  sources: RevenueReportData["bySource"]
  roomTypes: RevenueReportData["byRoomType"]
}): string {
  const rows: Array<Array<string | number>> = [
    ["Báo cáo doanh thu", `${range.from} đến ${range.to}`],
    [],
    ["Chỉ số", "Giá trị"],
    ["ADR", report.occupancy.adr],
    ["RevPAR", report.occupancy.revPar],
    ["Tỷ lệ lấp đầy (%)", report.occupancy.occupancyRatePercent],
    [],
    ["Doanh thu theo ngày"],
    ["Ngày", "Doanh thu", "Hoa hồng OTA", "Số booking"],
    ...report.daily.map((item) => [item.date, item.revenue, item.otaCommission, item.bookingCount]),
    [],
    ["Doanh thu theo tháng"],
    ["Tháng", "Doanh thu", "Hoa hồng OTA", "Số booking"],
    ...report.monthly.map((item) => [item.month, item.revenue, item.otaCommission, item.bookingCount]),
    [],
    ["Doanh thu theo nguồn"],
    ["Mã nguồn", "Nguồn", "Doanh thu", "Hoa hồng OTA", "Số booking"],
    ...sources.map((item) => [item.sourceCode, item.sourceName, item.revenue, item.otaCommission, item.bookingCount]),
    [],
    ["Top loại phòng theo doanh thu"],
    ["Mã loại phòng", "Loại phòng", "Đêm phòng", "ADR", "Doanh thu"],
    ...roomTypes.map((item) => [item.roomTypeCode, item.roomTypeName, item.roomNights, item.adr, item.revenue]),
  ]
  return rows.map((row) => row.map(csvValue).join(",")).join("\r\n")
}
