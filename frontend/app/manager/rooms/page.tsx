"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { BedDouble, Building2, FilterX, Plus, RefreshCw, Search, Wrench } from "lucide-react"

import { MaintenanceSchedule } from "@/components/admin/maintenance/maintenance-schedule"
import { FloorMap } from "@/components/admin/rooms/floor-map"
import { RoomDetailSheet } from "@/components/admin/rooms/room-detail-sheet"
import { RoomFormDialog, roomViewLabels } from "@/components/admin/rooms/room-form-dialog"
import { RoomHousekeepingSheet } from "@/components/admin/rooms/room-housekeeping-sheet"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { DataTable } from "@/components/ui/dataTable"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getRoomTypes } from "@/lib/api/room-types"
import { getRoomOccupancy, getRooms } from "@/lib/api/rooms"
import { useAuth } from "@/lib/auth-context"
import type {
  HousekeepingStatus,
  Room,
  RoomBookingStatus,
  RoomOperationalStatus,
  RoomView,
} from "@/types/room"
import type { RoomType } from "@/types/room-type"

const PAGE_SIZE = 10
const ALL_FILTER = "__all__"
const NO_FLOOR_FILTER = "__none__"
type RoomTab = "table" | "maintenance" | "floor-map"

const housekeepingLabels: Record<HousekeepingStatus, string> = {
  CLEAN: "Sạch",
  DIRTY: "Bẩn",
  CLEANING: "Đang dọn",
}

const operationalLabels: Record<RoomOperationalStatus, string> = {
  ACTIVE: "Hoạt động",
  MAINTENANCE: "Bảo trì",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  RENOVATION: "Cải tạo",
}

function formatPrice(value: number, currency = "VND"): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value)
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Không thể tải danh sách phòng. Vui lòng thử lại."
}

export default function AdminRoomsPage() {
  const router = useRouter()
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [rooms, setRooms] = useState<Room[]>([])
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [occupancyByRoom, setOccupancyByRoom] = useState<Record<string, RoomBookingStatus | null>>({})
  const [occupancyLoading, setOccupancyLoading] = useState(true)
  const [occupancyError, setOccupancyError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const [roomTypeFilter, setRoomTypeFilter] = useState(ALL_FILTER)
  const [floorFilter, setFloorFilter] = useState(ALL_FILTER)
  const [viewFilter, setViewFilter] = useState(ALL_FILTER)
  const [housekeepingFilter, setHousekeepingFilter] = useState(ALL_FILTER)
  const [page, setPage] = useState(1)
  const [formOpen, setFormOpen] = useState(false)
  const [editingRoom, setEditingRoom] = useState<Room | null>(null)
  const [selectedRoom, setSelectedRoom] = useState<Room | null>(null)
  const [selectedFloorRoom, setSelectedFloorRoom] = useState<Room | null>(null)
  const [activeTab, setActiveTab] = useState<RoomTab>("table")

  const permissions = user?.permissions ?? []
  const canRead = permissions.includes("room:read")
  const canCreate = permissions.includes("room:create")
  const canUpdate = permissions.includes("room:update")
  const canUpdateHousekeeping = permissions.includes("room:housekeeping:update")
  const canReadOccupancy = permissions.includes("room:occupancy:read")

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/manager/login?redirect=%2Fmanager%2Frooms")
    }
  }, [isAuthLoading, isAuthenticated, router])

  const loadData = useCallback(async () => {
    if (!canRead) {
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setLoadError(null)
    try {
      const [roomData, roomTypeData] = await Promise.all([getRooms(), getRoomTypes()])
      setRooms(roomData)
      setRoomTypes(roomTypeData)
      setSelectedRoom((current) => {
        if (!current) return null
        return roomData.find((room) => room.roomNumber === current.roomNumber) ?? null
      })
      setSelectedFloorRoom((current) => {
        if (!current) return null
        return roomData.find((room) => room.roomNumber === current.roomNumber) ?? null
      })
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }, [canRead])

  const loadOccupancy = useCallback(async () => {
    if (!canReadOccupancy) {
      setOccupancyByRoom({})
      setOccupancyLoading(false)
      setOccupancyError(null)
      return
    }
    setOccupancyLoading(true)
    try {
      const occupancy = await getRoomOccupancy()
      setOccupancyByRoom(Object.fromEntries(
        occupancy.map((item) => [item.roomNumber, item.bookingStatus])
      ))
      setOccupancyError(null)
    } catch (error) {
      setOccupancyError(getErrorMessage(error))
    } finally {
      setOccupancyLoading(false)
    }
  }, [canReadOccupancy])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadData(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadData])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadOccupancy(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadOccupancy])

  const floors = useMemo(() => {
    return [...new Set(rooms.map((room) => room.floor).filter((floor): floor is number => floor !== null))]
      .sort((left, right) => left - right)
  }, [rooms])

  const roomTypeByCode = useMemo(() => {
    return new Map(roomTypes.map((roomType) => [roomType.code, roomType]))
  }, [roomTypes])

  const filteredRooms = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase("vi")
    return rooms.filter((room) => {
      const matchesSearch = !normalizedSearch ||
        room.roomNumber.toLocaleLowerCase("vi").includes(normalizedSearch) ||
        room.roomTypeName.toLocaleLowerCase("vi").includes(normalizedSearch)
      const matchesRoomType = roomTypeFilter === ALL_FILTER || room.roomTypeCode === roomTypeFilter
      const matchesFloor = floorFilter === ALL_FILTER ||
        (floorFilter === NO_FLOOR_FILTER ? room.floor === null : room.floor === Number(floorFilter))
      const matchesView = viewFilter === ALL_FILTER || room.viewType === viewFilter
      const matchesHousekeeping = housekeepingFilter === ALL_FILTER ||
        room.housekeepingStatus === housekeepingFilter
      return matchesSearch && matchesRoomType && matchesFloor && matchesView && matchesHousekeeping
    })
  }, [floorFilter, housekeepingFilter, roomTypeFilter, rooms, search, viewFilter])

  const totalPages = Math.max(1, Math.ceil(filteredRooms.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pagedRooms = filteredRooms.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  function resetPage() {
    setPage(1)
  }

  function clearFilters() {
    setSearch("")
    setRoomTypeFilter(ALL_FILTER)
    setFloorFilter(ALL_FILTER)
    setViewFilter(ALL_FILTER)
    setHousekeepingFilter(ALL_FILTER)
    setPage(1)
  }

  function openCreateForm() {
    setEditingRoom(null)
    setFormOpen(true)
  }

  function openEditForm(room: Room) {
    setSelectedRoom(null)
    setSelectedFloorRoom(null)
    setEditingRoom(room)
    setFormOpen(true)
  }

  function handleHousekeepingUpdated(updatedRoom: Room) {
    setRooms((currentRooms) => currentRooms.map((room) => (
      room.roomNumber === updatedRoom.roomNumber ? updatedRoom : room
    )))
    setSelectedFloorRoom(updatedRoom)
  }

  const columns = [
    {
      key: "roomNumber",
      header: "Phòng",
      render: (room: Room) => (
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[var(--muted)]">
            <BedDouble className="h-4 w-4" />
          </div>
          <span className="font-semibold">{room.roomNumber}</span>
        </div>
      ),
    },
    {
      key: "roomTypeName",
      header: "Loại phòng",
      render: (room: Room) => (
        <div className="min-w-40">
          <p>{room.roomTypeName}</p>
          <p className="text-xs text-[var(--muted-foreground)]">{room.roomTypeCode}</p>
        </div>
      ),
    },
    {
      key: "floor",
      header: "Tầng / View",
      render: (room: Room) => (
        <div>
          <p>{room.floor === null ? "Chưa gán" : `Tầng ${room.floor}`}</p>
          <p className="text-xs text-[var(--muted-foreground)]">{roomViewLabels[room.viewType]}</p>
        </div>
      ),
    },
    {
      key: "operationalStatus",
      header: "Vận hành",
      render: (room: Room) => (
        <Badge variant={room.operationalStatus === "ACTIVE" ? "success" : "destructive"}>
          {operationalLabels[room.operationalStatus]}
        </Badge>
      ),
    },
    {
      key: "housekeepingStatus",
      header: "Housekeeping",
      render: (room: Room) => (
        <Badge variant={
          room.housekeepingStatus === "CLEAN"
            ? "success"
            : room.housekeepingStatus === "DIRTY"
              ? "destructive"
              : room.housekeepingStatus === "CLEANING"
                ? "warning"
                : "secondary"
        }>
          {housekeepingLabels[room.housekeepingStatus]}
        </Badge>
      ),
    },
    {
      key: "price",
      header: "Giá/đêm",
      className: "text-right",
      render: (room: Room) => {
        const roomType = roomTypeByCode.get(room.roomTypeCode)
        const price = room.priceOverride ?? roomType?.basePrice
        return (
          <div className="min-w-28">
            <p className="font-medium">{price === undefined ? "—" : formatPrice(price, roomType?.currency)}</p>
            <p className="text-xs text-[var(--muted-foreground)]">
              {room.priceOverride === null ? "Giá loại phòng" : "Giá riêng"}
            </p>
          </div>
        )
      },
    },
  ]

  if (isAuthLoading || (!isAuthenticated && !user)) {
    return <PageSkeleton />
  }

  if (!canRead) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <BedDouble className="h-10 w-10 text-[var(--muted-foreground)]" />
          <h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Tài khoản cần permission <code>room:read</code> để xem danh sách phòng.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Quản lý phòng</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Theo dõi phòng theo danh sách, sơ đồ tầng, lịch bảo trì và trạng thái housekeeping.
          </p>
        </div>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={(value) => setActiveTab(value as RoomTab)}
        className="flex flex-col gap-4"
      >
        <div>
          <TabsList>
            <TabsTrigger value="table"><BedDouble className="mr-2 h-4 w-4" /> Danh sách</TabsTrigger>
            <TabsTrigger value="maintenance"><Wrench className="mr-2 h-4 w-4" /> Lịch bảo trì</TabsTrigger>
            <TabsTrigger value="floor-map"><Building2 className="mr-2 h-4 w-4" /> Sơ đồ tầng</TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="table" className="mt-0 flex flex-col gap-4">
          {canCreate && (
            <div className="flex justify-end">
              <Button onClick={openCreateForm} disabled={!roomTypes.some((roomType) => roomType.isActive)}>
                <Plus className="mr-2 h-4 w-4" /> Thêm phòng
              </Button>
            </div>
          )}
          <RoomFilters
            search={search}
            roomTypeFilter={roomTypeFilter}
            floorFilter={floorFilter}
            viewFilter={viewFilter}
            housekeepingFilter={housekeepingFilter}
            roomTypes={roomTypes}
            floors={floors}
            rooms={rooms}
            onSearchChange={(value) => { setSearch(value); resetPage() }}
            onRoomTypeChange={(value) => { setRoomTypeFilter(value); resetPage() }}
            onFloorChange={(value) => { setFloorFilter(value); resetPage() }}
            onViewChange={(value) => { setViewFilter(value); resetPage() }}
            onHousekeepingChange={(value) => { setHousekeepingFilter(value); resetPage() }}
            onClear={clearFilters}
          />
          {isLoading ? (
            <PageSkeleton tableOnly />
          ) : loadError ? (
            <RoomLoadError message={loadError} onRetry={() => void loadData()} />
          ) : (
            <DataTable
              columns={columns}
              data={pagedRooms}
              keyExtractor={(room) => room.roomNumber}
              onRowClick={setSelectedRoom}
              emptyMessage="Không có phòng phù hợp với bộ lọc"
              pagination={{
                page: currentPage,
                pageSize: PAGE_SIZE,
                total: filteredRooms.length,
                onPageChange: setPage,
              }}
              actions={canUpdate ? [{ label: "Sửa", onClick: openEditForm }] : undefined}
            />
          )}
        </TabsContent>

        <TabsContent value="floor-map" className="mt-0 flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <SummaryCard label="Tổng phòng" value={rooms.length} iconClass="bg-slate-500" />
            <SummaryCard label="Sạch" value={rooms.filter((room) => room.housekeepingStatus === "CLEAN").length} iconClass="bg-green-500" />
            <SummaryCard label="Bẩn" value={rooms.filter((room) => room.housekeepingStatus === "DIRTY").length} iconClass="bg-red-500" />
            <SummaryCard label="Đang dọn" value={rooms.filter((room) => room.housekeepingStatus === "CLEANING").length} iconClass="bg-orange-500" />
          </div>
          <RoomFilters
            search={search}
            roomTypeFilter={roomTypeFilter}
            floorFilter={floorFilter}
            viewFilter={viewFilter}
            housekeepingFilter={housekeepingFilter}
            roomTypes={roomTypes}
            floors={floors}
            rooms={rooms}
            onSearchChange={(value) => { setSearch(value); resetPage() }}
            onRoomTypeChange={(value) => { setRoomTypeFilter(value); resetPage() }}
            onFloorChange={(value) => { setFloorFilter(value); resetPage() }}
            onViewChange={(value) => { setViewFilter(value); resetPage() }}
            onHousekeepingChange={(value) => { setHousekeepingFilter(value); resetPage() }}
            onClear={clearFilters}
          />
          {isLoading ? (
            <PageSkeleton tableOnly />
          ) : loadError ? (
            <RoomLoadError message={loadError} onRetry={() => void loadData()} />
          ) : (
            <FloorMap
              rooms={filteredRooms}
              occupancyByRoom={occupancyByRoom}
              occupancyLoading={occupancyLoading}
              occupancyError={occupancyError}
              onSelectRoom={setSelectedFloorRoom}
            />
          )}
        </TabsContent>

        <TabsContent value="maintenance" className="mt-0">
          <MaintenanceSchedule />
        </TabsContent>
      </Tabs>

      <RoomFormDialog
        open={formOpen}
        room={editingRoom}
        roomTypes={roomTypes}
        onOpenChange={setFormOpen}
        onSaved={loadData}
      />

      <RoomDetailSheet
        room={selectedRoom}
        roomTypes={roomTypes}
        canUpdate={canUpdate}
        onOpenChange={(open) => !open && setSelectedRoom(null)}
        onEdit={openEditForm}
      />

      <RoomHousekeepingSheet
        room={selectedFloorRoom}
        bookingStatus={selectedFloorRoom ? occupancyByRoom[selectedFloorRoom.roomNumber] ?? null : null}
        canUpdate={canUpdateHousekeeping}
        onOpenChange={(open) => !open && setSelectedFloorRoom(null)}
        onUpdated={handleHousekeepingUpdated}
      />
    </div>
  )
}

function SummaryCard({ label, value, iconClass }: { label: string; value: number; iconClass: string }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between p-5">
        <div>
          <p className="text-sm text-[var(--muted-foreground)]">{label}</p>
          <p className="mt-1 text-2xl font-bold">{value}</p>
        </div>
        <span className={`h-3 w-3 rounded-full ${iconClass}`} />
      </CardContent>
    </Card>
  )
}

interface RoomFiltersProps {
  search: string
  roomTypeFilter: string
  floorFilter: string
  viewFilter: string
  housekeepingFilter: string
  roomTypes: RoomType[]
  floors: number[]
  rooms: Room[]
  onSearchChange: (value: string) => void
  onRoomTypeChange: (value: string) => void
  onFloorChange: (value: string) => void
  onViewChange: (value: string) => void
  onHousekeepingChange: (value: string) => void
  onClear: () => void
}

function RoomFilters({
  search,
  roomTypeFilter,
  floorFilter,
  viewFilter,
  housekeepingFilter,
  roomTypes,
  floors,
  rooms,
  onSearchChange,
  onRoomTypeChange,
  onFloorChange,
  onViewChange,
  onHousekeepingChange,
  onClear,
}: RoomFiltersProps) {
  return (
    <Card>
      <CardContent className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-6">
        <div className="relative md:col-span-2 xl:col-span-2">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <Input
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Tìm số phòng hoặc loại phòng..."
            className="pl-9"
          />
        </div>

        <Select value={roomTypeFilter} onValueChange={onRoomTypeChange}>
          <SelectTrigger><SelectValue placeholder="Loại phòng" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_FILTER}>Tất cả loại phòng</SelectItem>
            {roomTypes.map((roomType) => (
              <SelectItem key={roomType.code} value={roomType.code}>{roomType.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={floorFilter} onValueChange={onFloorChange}>
          <SelectTrigger><SelectValue placeholder="Tầng" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_FILTER}>Tất cả tầng</SelectItem>
            {floors.map((floor) => <SelectItem key={floor} value={String(floor)}>Tầng {floor}</SelectItem>)}
            {rooms.some((room) => room.floor === null) && (
              <SelectItem value={NO_FLOOR_FILTER}>Chưa gán tầng</SelectItem>
            )}
          </SelectContent>
        </Select>

        <Select value={viewFilter} onValueChange={onViewChange}>
          <SelectTrigger><SelectValue placeholder="View" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_FILTER}>Tất cả view</SelectItem>
            {(Object.keys(roomViewLabels) as RoomView[]).map((view) => (
              <SelectItem key={view} value={view}>{roomViewLabels[view]}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={housekeepingFilter} onValueChange={onHousekeepingChange}>
          <SelectTrigger><SelectValue placeholder="Housekeeping" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_FILTER}>Tất cả HK</SelectItem>
            {(Object.keys(housekeepingLabels) as HousekeepingStatus[]).map((status) => (
              <SelectItem key={status} value={status}>{housekeepingLabels[status]}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Button type="button" variant="outline" onClick={onClear}>
          <FilterX className="mr-2 h-4 w-4" /> Xóa lọc
        </Button>
      </CardContent>
    </Card>
  )
}

function RoomLoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Card>
      <CardContent className="flex min-h-48 flex-col items-center justify-center gap-3 text-center">
        <p className="text-sm text-[var(--destructive)]">{message}</p>
        <Button variant="outline" onClick={onRetry}>
          <RefreshCw className="mr-2 h-4 w-4" /> Thử lại
        </Button>
      </CardContent>
    </Card>
  )
}

function PageSkeleton({ tableOnly = false }: { tableOnly?: boolean }) {
  return (
    <div className="flex flex-col gap-4">
      {!tableOnly && <Skeleton className="h-16 w-full" />}
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-80 w-full" />
    </div>
  )
}
