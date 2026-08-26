"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { format, parseISO, startOfMonth } from "date-fns"
import { vi } from "date-fns/locale"
import { BadgeDollarSign, CalendarDays, List, Plus, RefreshCw, Search } from "lucide-react"
import { useRouter } from "next/navigation"

import { PricingCalendar } from "@/components/admin/pricing/pricing-calendar"
import { RateOverrideDetailSheet } from "@/components/admin/pricing/rate-override-detail-sheet"
import { RateOverrideFormDialog } from "@/components/admin/pricing/rate-override-form-dialog"
import { formatMoney, formatWeekdays } from "@/components/admin/pricing/pricing-utils"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { DataTable } from "@/components/ui/dataTable"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getActiveRateOverrides } from "@/lib/api/rate-overrides"
import { getRoomTypes } from "@/lib/api/room-types"
import { useAuth } from "@/lib/auth-context"
import type { RateOverride } from "@/types/rate-override"
import type { RoomType } from "@/types/room-type"

const PAGE_SIZE = 10
const ALL_TARGETS = "__all__"

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Không thể tải dữ liệu giá. Vui lòng thử lại."
}

export default function AdminPricingPage() {
  const router = useRouter()
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [overrides, setOverrides] = useState<RateOverride[]>([])
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const [roomTypeFilter, setRoomTypeFilter] = useState(ALL_TARGETS)
  const [page, setPage] = useState(1)
  const [activeTab, setActiveTab] = useState("list")
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()))
  const [formOpen, setFormOpen] = useState(false)
  const [selectedOverride, setSelectedOverride] = useState<RateOverride | null>(null)

  const permissions = user?.permissions ?? []
  const canManagePricing = permissions.includes("pricing:manage")

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/manager/login?redirect=%2Fmanager%2Fpricing")
    }
  }, [isAuthLoading, isAuthenticated, router])

  const loadData = useCallback(async () => {
    if (!canManagePricing) {
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const [overrideData, roomTypeData] = await Promise.all([
        getActiveRateOverrides(),
        getRoomTypes(),
      ])
      setOverrides(overrideData)
      setRoomTypes(roomTypeData)
      setSelectedOverride((selected) => selected
        ? overrideData.find((item) => item.id === selected.id) ?? null
        : null)
    } catch (loadError) {
      setError(getErrorMessage(loadError))
    } finally {
      setIsLoading(false)
    }
  }, [canManagePricing])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadData(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadData])

  const filteredOverrides = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("vi")
    return overrides.filter((override) => {
      const target = `${override.roomTypeCode} ${override.roomTypeName}`
      const matchesSearch = !query || `${override.name} ${target}`.toLocaleLowerCase("vi").includes(query)
      const matchesTarget = roomTypeFilter === ALL_TARGETS || override.roomTypeCode === roomTypeFilter
      return matchesSearch && matchesTarget
    })
  }, [overrides, roomTypeFilter, search])

  const filteredRoomTypes = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("vi")
    return roomTypes.filter((roomType) => {
      const matchesSearch = !query || `${roomType.code} ${roomType.name}`.toLocaleLowerCase("vi").includes(query)
      const matchesTarget = roomTypeFilter === ALL_TARGETS || roomType.code === roomTypeFilter
      return matchesSearch && matchesTarget
    })
  }, [roomTypeFilter, roomTypes, search])

  const totalPages = Math.max(1, Math.ceil(filteredOverrides.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const pagedOverrides = filteredOverrides.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  useEffect(() => {
    const timer = window.setTimeout(() => setPage(1), 0)
    return () => window.clearTimeout(timer)
  }, [roomTypeFilter, search])

  async function handleCreated(created: RateOverride) {
    setCurrentMonth(startOfMonth(parseISO(created.startDate)))
    await loadData()
  }

  if (isAuthLoading || (!isAuthenticated && !user)) return <PageSkeleton />

  if (!canManagePricing) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <BadgeDollarSign className="h-10 w-10 text-[var(--muted-foreground)]" />
          <h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Tài khoản cần permission <code>pricing:manage</code> để quản lý giá.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Quản lý giá</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Thiết lập rate override theo loại phòng và xem giá hiệu lực từng ngày.
          </p>
        </div>
        <Button onClick={() => setFormOpen(true)} disabled={roomTypes.length === 0}>
          <Plus className="mr-2 h-4 w-4" /> Tạo rate override
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Rule đang hoạt động" value={overrides.length} />
        <SummaryCard label="Loại phòng có rule" value={new Set(overrides.map((item) => item.roomTypeCode)).size} />
        <SummaryCard label="Priority cao nhất" value={overrides.length ? Math.max(...overrides.map((item) => item.priority)) : 0} />
      </div>

      <Card>
        <CardContent className="grid gap-3 p-4 md:grid-cols-2">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
            <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm tên rule hoặc đối tượng..." className="pl-9" />
          </div>
          <Select value={roomTypeFilter} onValueChange={setRoomTypeFilter}>
            <SelectTrigger><SelectValue placeholder="Loại phòng" /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_TARGETS}>Tất cả loại phòng</SelectItem>
              {roomTypes.map((roomType) => (
                <SelectItem key={roomType.code} value={roomType.code}>{roomType.code} · {roomType.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      {isLoading ? <PageSkeleton tableOnly /> : error ? (
        <Card>
          <CardContent className="flex min-h-52 flex-col items-center justify-center gap-3 text-center">
            <p className="text-sm text-[var(--destructive)]">{error}</p>
            <Button variant="outline" onClick={() => void loadData()}><RefreshCw className="mr-2 h-4 w-4" /> Thử lại</Button>
          </CardContent>
        </Card>
      ) : (
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="list"><List className="mr-2 h-4 w-4" /> Danh sách</TabsTrigger>
            <TabsTrigger value="calendar"><CalendarDays className="mr-2 h-4 w-4" /> Lịch giá</TabsTrigger>
          </TabsList>

          <TabsContent value="list" className="mt-4">
            <DataTable
              data={pagedOverrides}
              keyExtractor={(row) => String(row.id)}
              onRowClick={setSelectedOverride}
              emptyMessage="Không có rate override phù hợp."
              columns={[
                { key: "name", header: "Rule", render: (row) => <div><p className="font-medium">{row.name}</p><p className="text-xs text-[var(--muted-foreground)]">#{row.id}</p></div> },
                { key: "target", header: "Loại phòng", render: (row) => <div><p className="font-medium">{row.roomTypeName}</p><Badge variant="outline">{row.roomTypeCode}</Badge></div> },
                { key: "dates", header: "Khoảng ngày", render: (row) => <div><p>{format(parseISO(row.startDate), "dd/MM/yyyy", { locale: vi })}</p><p className="text-xs text-[var(--muted-foreground)]">đến {format(parseISO(row.endDate), "dd/MM/yyyy", { locale: vi })} (không gồm)</p></div> },
                { key: "weekdays", header: "Ngày áp dụng", render: (row) => formatWeekdays(row.weekdays) },
                { key: "price", header: "Giá", render: (row) => <span className="font-semibold">{formatMoney(row.price)}</span> },
                { key: "priority", header: "Priority", render: (row) => <Badge>{row.priority}</Badge> },
                { key: "status", header: "Trạng thái", render: () => <Badge variant="outline">Đang hoạt động</Badge> },
              ]}
              pagination={{ page: safePage, pageSize: PAGE_SIZE, total: filteredOverrides.length, onPageChange: setPage }}
            />
          </TabsContent>

          <TabsContent value="calendar" className="mt-4">
            <PricingCalendar month={currentMonth} roomTypes={filteredRoomTypes} activeOverrides={overrides} onMonthChange={setCurrentMonth} onSelectOverride={setSelectedOverride} />
          </TabsContent>
        </Tabs>
      )}

      <RateOverrideFormDialog open={formOpen} roomTypes={roomTypes} activeOverrides={overrides} onOpenChange={setFormOpen} onCreated={handleCreated} />
      <RateOverrideDetailSheet override={selectedOverride} onOpenChange={(open) => !open && setSelectedOverride(null)} />
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return <Card><CardContent className="p-5"><p className="text-sm text-[var(--muted-foreground)]">{label}</p><p className="mt-1 text-2xl font-bold">{value}</p></CardContent></Card>
}

function PageSkeleton({ tableOnly = false }: { tableOnly?: boolean }) {
  return <div className="space-y-4">{!tableOnly && <Skeleton className="h-16 w-full" />}<Skeleton className="h-24 w-full" /><Skeleton className="h-96 w-full" /></div>
}
