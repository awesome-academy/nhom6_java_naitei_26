"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import {
  addDays,
  addMonths,
  format,
  isSameMonth,
  parseISO,
  startOfMonth,
} from "date-fns"
import { vi } from "date-fns/locale"
import {
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  FilterX,
  Plus,
  RefreshCw,
  Search,
  Wrench,
} from "lucide-react"

import { BlockDetailSheet } from "@/components/admin/maintenance/block-detail-sheet"
import { BlockFormDialog } from "@/components/admin/maintenance/block-form-dialog"
import { MaintenanceCalendar } from "@/components/admin/maintenance/maintenance-calendar"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { getRoomStatusBlocks } from "@/lib/api/room-status-blocks"
import { getRooms } from "@/lib/api/rooms"
import { useAuth } from "@/lib/auth-context"
import type { Room } from "@/types/room"
import type { RoomStatusBlock } from "@/types/room-status-block"

const ALL_FILTER = "__all__"
const NO_FLOOR_FILTER = "__none__"

interface FormDefaults {
  roomNumber: string
  startDate: string
  endDate: string
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Không thể tải lịch bảo trì. Vui lòng thử lại."
}

function getDefaultDate(month: Date): Date {
  const today = new Date()
  return isSameMonth(today, month) ? today : startOfMonth(month)
}

export default function ManagerMaintenancePage() {
  const router = useRouter()
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()))
  const [rooms, setRooms] = useState<Room[]>([])
  const [blocks, setBlocks] = useState<RoomStatusBlock[]>([])
  const [isRoomsLoading, setIsRoomsLoading] = useState(true)
  const [isBlocksLoading, setIsBlocksLoading] = useState(true)
  const [roomsError, setRoomsError] = useState<string | null>(null)
  const [blocksError, setBlocksError] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const [roomTypeFilter, setRoomTypeFilter] = useState(ALL_FILTER)
  const [floorFilter, setFloorFilter] = useState(ALL_FILTER)
  const [formOpen, setFormOpen] = useState(false)
  const [formDefaults, setFormDefaults] = useState<FormDefaults>({
    roomNumber: "",
    startDate: "",
    endDate: "",
  })
  const [selectedBlock, setSelectedBlock] = useState<RoomStatusBlock | null>(null)

  const permissions = user?.permissions ?? []
  const canRead = permissions.includes("room:read")
  const canManageMaintenance = permissions.includes("maintenance:manage")

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/manager/login?redirect=%2Fmanager%2Fmaintenance")
    }
  }, [isAuthLoading, isAuthenticated, router])

  const loadRooms = useCallback(async () => {
    if (!canRead) {
      setIsRoomsLoading(false)
      return
    }
    setIsRoomsLoading(true)
    setRoomsError(null)
    try {
      setRooms(await getRooms())
    } catch (error) {
      setRoomsError(getErrorMessage(error))
    } finally {
      setIsRoomsLoading(false)
    }
  }, [canRead])

  const loadBlocks = useCallback(async () => {
    if (!canRead) {
      setIsBlocksLoading(false)
      return
    }
    setIsBlocksLoading(true)
    setBlocksError(null)
    const monthStart = startOfMonth(currentMonth)
    try {
      const blockData = await getRoomStatusBlocks(
        format(monthStart, "yyyy-MM-dd"),
        format(addMonths(monthStart, 1), "yyyy-MM-dd")
      )
      setBlocks(blockData)
      setSelectedBlock((current) => {
        if (!current) return null
        return blockData.find((block) => block.publicId === current.publicId) ?? null
      })
    } catch (error) {
      setBlocksError(getErrorMessage(error))
    } finally {
      setIsBlocksLoading(false)
    }
  }, [canRead, currentMonth])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadRooms(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadRooms])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadBlocks(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadBlocks])

  const roomTypeOptions = useMemo(() => {
    const values = new Map<string, string>()
    rooms.forEach((room) => values.set(room.roomTypeCode, room.roomTypeName))
    return [...values.entries()].sort((left, right) => left[1].localeCompare(right[1], "vi"))
  }, [rooms])

  const floorOptions = useMemo(() => {
    return [...new Set(rooms.map((room) => room.floor).filter((floor): floor is number => floor !== null))]
      .sort((left, right) => left - right)
  }, [rooms])

  const filteredRooms = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase("vi")
    return rooms.filter((room) => {
      const matchesSearch = !normalizedSearch ||
        room.roomNumber.toLocaleLowerCase("vi").includes(normalizedSearch) ||
        room.roomTypeName.toLocaleLowerCase("vi").includes(normalizedSearch)
      const matchesRoomType = roomTypeFilter === ALL_FILTER || room.roomTypeCode === roomTypeFilter
      const matchesFloor = floorFilter === ALL_FILTER ||
        (floorFilter === NO_FLOOR_FILTER ? room.floor === null : room.floor === Number(floorFilter))
      return matchesSearch && matchesRoomType && matchesFloor
    })
  }, [floorFilter, roomTypeFilter, rooms, search])

  const affectedRoomCount = useMemo(
    () => new Set(blocks.map((block) => block.roomNumber)).size,
    [blocks]
  )

  function clearFilters() {
    setSearch("")
    setRoomTypeFilter(ALL_FILTER)
    setFloorFilter(ALL_FILTER)
  }

  function openCreateDialog(room?: Room, date?: Date) {
    const startDate = date ?? getDefaultDate(currentMonth)
    setFormDefaults({
      roomNumber: room?.roomNumber ?? filteredRooms[0]?.roomNumber ?? rooms[0]?.roomNumber ?? "",
      startDate: format(startDate, "yyyy-MM-dd"),
      endDate: format(addDays(startDate, 1), "yyyy-MM-dd"),
    })
    setFormOpen(true)
  }

  async function handleCreated(block: RoomStatusBlock) {
    const targetMonth = startOfMonth(parseISO(block.startDate))
    if (isSameMonth(targetMonth, currentMonth)) {
      await loadBlocks()
    } else {
      setCurrentMonth(targetMonth)
    }
  }

  if (isAuthLoading || (!isAuthenticated && !user)) {
    return <PageSkeleton />
  }

  if (!canRead) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <Wrench className="h-10 w-10 text-[var(--muted-foreground)]" />
          <h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Tài khoản cần permission <code>room:read</code> để xem lịch bảo trì.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="min-w-0 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Lịch bảo trì phòng</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Theo dõi và chặn bán phòng theo từng khoảng ngày vận hành.
          </p>
        </div>
        {canManageMaintenance && (
          <Button onClick={() => openCreateDialog()} disabled={rooms.length === 0}>
            <Plus className="mr-2 h-4 w-4" /> Tạo lịch bảo trì
          </Button>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Block trong tháng" value={blocks.length} />
        <SummaryCard label="Phòng bị ảnh hưởng" value={affectedRoomCount} />
        <SummaryCard label="Phòng đang hiển thị" value={filteredRooms.length} />
      </div>

      <Card>
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="Tháng trước"
                onClick={() => {
                  setSelectedBlock(null)
                  setCurrentMonth((month) => addMonths(month, -1))
                }}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setSelectedBlock(null)
                  setCurrentMonth(startOfMonth(new Date()))
                }}
              >
                Hôm nay
              </Button>
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="Tháng sau"
                onClick={() => {
                  setSelectedBlock(null)
                  setCurrentMonth((month) => addMonths(month, 1))
                }}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
            <div className="flex items-center gap-2 text-lg font-semibold capitalize">
              <CalendarClock className="h-5 w-5 text-[var(--accent)]" />
              {format(currentMonth, "MMMM yyyy", { locale: vi })}
            </div>
          </div>

          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Tìm số hoặc loại phòng..."
                className="pl-9"
              />
            </div>
            <Select value={roomTypeFilter} onValueChange={setRoomTypeFilter}>
              <SelectTrigger><SelectValue placeholder="Loại phòng" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_FILTER}>Tất cả loại phòng</SelectItem>
                {roomTypeOptions.map(([code, name]) => (
                  <SelectItem key={code} value={code}>{name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={floorFilter} onValueChange={setFloorFilter}>
              <SelectTrigger><SelectValue placeholder="Tầng" /></SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_FILTER}>Tất cả tầng</SelectItem>
                {floorOptions.map((floor) => (
                  <SelectItem key={floor} value={String(floor)}>Tầng {floor}</SelectItem>
                ))}
                {rooms.some((room) => room.floor === null) && (
                  <SelectItem value={NO_FLOOR_FILTER}>Chưa gán tầng</SelectItem>
                )}
              </SelectContent>
            </Select>
            <Button type="button" variant="outline" onClick={clearFilters}>
              <FilterX className="mr-2 h-4 w-4" /> Xóa lọc
            </Button>
          </div>
        </CardContent>
      </Card>

      {isRoomsLoading || isBlocksLoading ? (
        <PageSkeleton tableOnly />
      ) : roomsError || blocksError ? (
        <Card>
          <CardContent className="flex min-h-52 flex-col items-center justify-center gap-3 text-center">
            <p className="text-sm text-[var(--destructive)]">{roomsError || blocksError}</p>
            <Button
              variant="outline"
              onClick={() => void Promise.all([loadRooms(), loadBlocks()])}
            >
              <RefreshCw className="mr-2 h-4 w-4" /> Thử lại
            </Button>
          </CardContent>
        </Card>
      ) : (
        <MaintenanceCalendar
          month={currentMonth}
          rooms={filteredRooms}
          blocks={blocks}
          canCreate={canManageMaintenance}
          onSelectEmptyCell={(room, date) => openCreateDialog(room, date)}
          onSelectBlock={setSelectedBlock}
        />
      )}

      <BlockFormDialog
        open={formOpen}
        rooms={rooms}
        initialRoomNumber={formDefaults.roomNumber}
        initialStartDate={formDefaults.startDate}
        initialEndDate={formDefaults.endDate}
        onOpenChange={setFormOpen}
        onCreated={handleCreated}
      />

      {selectedBlock && (
        <BlockDetailSheet
          key={selectedBlock.publicId}
          block={selectedBlock}
          room={rooms.find((room) => room.roomNumber === selectedBlock.roomNumber) ?? null}
          canManage={canManageMaintenance}
          onOpenChange={(open) => !open && setSelectedBlock(null)}
          onChanged={loadBlocks}
        />
      )}
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-[var(--muted-foreground)]">{label}</p>
        <p className="mt-1 text-2xl font-bold">{value}</p>
      </CardContent>
    </Card>
  )
}

function PageSkeleton({ tableOnly = false }: { tableOnly?: boolean }) {
  return (
    <div className="space-y-4">
      {!tableOnly && <Skeleton className="h-16 w-full" />}
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-96 w-full" />
    </div>
  )
}
