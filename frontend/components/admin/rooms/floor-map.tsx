import { BedDouble, Building2, Wrench } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import type { HousekeepingStatus, Room, RoomBookingStatus } from "@/types/room"

const tileStyles: Record<HousekeepingStatus, string> = {
  CLEAN: "border-green-300 bg-green-50 text-green-950 hover:bg-green-100",
  DIRTY: "border-red-300 bg-red-50 text-red-950 hover:bg-red-100",
  CLEANING: "border-orange-300 bg-orange-50 text-orange-950 hover:bg-orange-100",
}

const housekeepingLabels: Record<HousekeepingStatus, string> = {
  CLEAN: "Sạch",
  DIRTY: "Bẩn",
  CLEANING: "Đang dọn",
}

const bookingLabels: Record<RoomBookingStatus, string> = {
  HELD: "Đang giữ",
  RESERVED: "Đã đặt",
  OCCUPIED: "Đang ở",
}

const operationalLabels: Record<Room["operationalStatus"], string> = {
  ACTIVE: "Hoạt động",
  MAINTENANCE: "Bảo trì",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  RENOVATION: "Cải tạo",
}

interface FloorMapProps {
  rooms: Room[]
  occupancyByRoom: Record<string, RoomBookingStatus | null>
  occupancyLoading: boolean
  occupancyError: string | null
  onSelectRoom: (room: Room) => void
}

export function FloorMap({
  rooms,
  occupancyByRoom,
  occupancyLoading,
  occupancyError,
  onSelectRoom,
}: FloorMapProps) {
  const groupedRooms = new Map<number | null, Room[]>()
  rooms.forEach((room) => {
    const current = groupedRooms.get(room.floor) ?? []
    current.push(room)
    groupedRooms.set(room.floor, current)
  })
  const floors = [...groupedRooms.entries()].sort(([left], [right]) => {
    if (left === null) return 1
    if (right === null) return -1
    return left - right
  })

  if (rooms.length === 0) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <Building2 className="h-10 w-10 text-[var(--muted-foreground)]" />
          <p className="font-medium">Không có phòng phù hợp với bộ lọc</p>
          <p className="text-sm text-[var(--muted-foreground)]">Thử bỏ bớt điều kiện để xem sơ đồ tầng.</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-4 rounded-xl border bg-[var(--card)] p-4 text-sm">
        <Legend color="bg-green-500" label="CLEAN — Sạch" />
        <Legend color="bg-red-500" label="DIRTY — Bẩn" />
        <Legend color="bg-orange-500" label="CLEANING — Đang dọn" />
        <Legend color="bg-blue-500" label="Đang ở / đã đặt / đang giữ" />
      </div>

      {occupancyError && (
        <p className="text-sm text-[var(--destructive)]">
          Không thể tải trạng thái đặt phòng: {occupancyError}
        </p>
      )}

      {floors.map(([floor, floorRooms]) => (
        <Card key={floor ?? "unassigned"}>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
            <CardTitle className="text-base">{floor === null ? "Chưa gán tầng" : `Tầng ${floor}`}</CardTitle>
            <span className="text-sm text-[var(--muted-foreground)]">{floorRooms.length} phòng</span>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
              {floorRooms.map((room) => (
                <button
                  key={room.roomNumber}
                  type="button"
                  onClick={() => onSelectRoom(room)}
                  className={cn(
                    "min-h-28 rounded-xl border p-3 text-left shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-[var(--ring)]",
                    tileStyles[room.housekeepingStatus]
                  )}
                >
                  <div className="flex items-start justify-between gap-2">
                    <BedDouble className="h-5 w-5" />
                    <div className="flex items-center gap-1">
                      {room.operationalStatus !== "ACTIVE" && (
                        <Wrench className="h-4 w-4" aria-label="Phòng không hoạt động" />
                      )}
                    </div>
                  </div>
                  <p className="mt-3 text-lg font-bold">{room.roomNumber}</p>
                  <p className="truncate text-xs opacity-75">{room.roomTypeName}</p>
                  <div className="mt-2 flex flex-wrap gap-1">
                    <Badge className="bg-white/70" variant="outline">
                      {housekeepingLabels[room.housekeepingStatus]}
                    </Badge>
                    <Badge className="bg-white/70" variant={getBookingVariant(occupancyByRoom[room.roomNumber])}>
                      {getBookingLabel(occupancyByRoom[room.roomNumber], occupancyLoading, occupancyError !== null)}
                    </Badge>
                  </div>
                  <p className="mt-2 truncate text-[11px] opacity-75">
                    {operationalLabels[room.operationalStatus]}
                  </p>
                </button>
              ))}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className={cn("h-3 w-3 rounded-full", color)} />
      <span>{label}</span>
    </div>
  )
}

function getBookingLabel(
  status: RoomBookingStatus | null | undefined,
  loading: boolean,
  hasError: boolean
): string {
  if (loading) return "Đang tải"
  if (hasError && status === undefined) return "Chưa xác định"
  return status ? bookingLabels[status] : "Trống"
}

function getBookingVariant(status: RoomBookingStatus | null | undefined) {
  if (status === "OCCUPIED") return "success" as const
  if (status === "RESERVED") return "confirmed" as const
  if (status === "HELD") return "warning" as const
  return "outline" as const
}
