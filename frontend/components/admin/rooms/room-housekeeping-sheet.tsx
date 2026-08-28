"use client"

import { useState } from "react"
import { BedDouble, Loader2 } from "lucide-react"
import { toast } from "sonner"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { updateHousekeepingStatus } from "@/lib/api/rooms"
import type { HousekeepingStatus, Room, RoomBookingStatus } from "@/types/room"

const housekeepingLabels: Record<HousekeepingStatus, string> = {
  CLEAN: "Sạch",
  DIRTY: "Bẩn",
  CLEANING: "Đang dọn",
}

const nextHousekeepingStatus: Record<HousekeepingStatus, HousekeepingStatus> = {
  CLEAN: "DIRTY",
  DIRTY: "CLEANING",
  CLEANING: "CLEAN",
}

const operationalLabels: Record<Room["operationalStatus"], string> = {
  ACTIVE: "Hoạt động",
  MAINTENANCE: "Bảo trì",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  RENOVATION: "Cải tạo",
}

const bookingLabels: Record<RoomBookingStatus, string> = {
  HELD: "Đang giữ phòng",
  RESERVED: "Đã đặt",
  OCCUPIED: "Đang ở",
}

interface RoomHousekeepingSheetProps {
  room: Room | null
  bookingStatus: RoomBookingStatus | null
  canUpdate: boolean
  onOpenChange: (open: boolean) => void
  onUpdated: (room: Room) => void
}

export function RoomHousekeepingSheet({
  room,
  bookingStatus,
  canUpdate,
  onOpenChange,
  onUpdated,
}: RoomHousekeepingSheetProps) {
  const [updatingRoomNumber, setUpdatingRoomNumber] = useState<string | null>(null)
  const isUpdating = room?.roomNumber === updatingRoomNumber

  async function handleUpdate() {
    if (!room || isUpdating) return

    const targetStatus = nextHousekeepingStatus[room.housekeepingStatus]
    setUpdatingRoomNumber(room.roomNumber)
    try {
      const updatedRoom = await updateHousekeepingStatus(room.roomNumber, targetStatus)
      onUpdated(updatedRoom)
      toast.success(`Đã chuyển phòng ${room.roomNumber} sang ${housekeepingLabels[targetStatus]}.`)
    } catch (error) {
      console.error("Failed to update room housekeeping status", error)
      toast.error(error instanceof Error ? error.message : "Không thể cập nhật trạng thái phòng.")
    } finally {
      setUpdatingRoomNumber((current) => current === room.roomNumber ? null : current)
    }
  }

  return (
    <Sheet open={room !== null} onOpenChange={onOpenChange}>
      <SheetContent>
        {room && (
          <>
            <SheetHeader className="border-b px-6 py-5 pr-12">
              <SheetTitle className="flex items-center gap-2">
                <BedDouble className="h-5 w-5" /> Phòng {room.roomNumber}
              </SheetTitle>
              <SheetDescription>
                Cập nhật trạng thái housekeeping của {room.roomTypeName} ({room.roomTypeCode})
              </SheetDescription>
            </SheetHeader>

            <div className="flex-1 space-y-5 overflow-y-auto px-6 py-5">
              <div className="grid gap-3 sm:grid-cols-2">
                <StatusBlock label="Housekeeping">
                  <Badge variant={getHousekeepingVariant(room.housekeepingStatus)}>
                    {housekeepingLabels[room.housekeepingStatus]}
                  </Badge>
                </StatusBlock>
                <StatusBlock label="Vận hành">
                  <Badge variant={room.operationalStatus === "ACTIVE" ? "success" : "destructive"}>
                    {operationalLabels[room.operationalStatus]}
                  </Badge>
                </StatusBlock>
              </div>

              <StatusBlock label="Trạng thái đặt phòng">
                {bookingStatus ? (
                  <Badge variant={getBookingVariant(bookingStatus)}>{bookingLabels[bookingStatus]}</Badge>
                ) : (
                  <span className="text-sm text-[var(--muted-foreground)]">Chưa có booking hiệu lực</span>
                )}
              </StatusBlock>

              <div className="rounded-lg border bg-[var(--muted)]/30 p-4 text-sm">
                <p className="font-medium">Chu trình housekeeping</p>
                <p className="mt-1 text-[var(--muted-foreground)]">Sạch → Bẩn → Đang dọn → Sạch</p>
              </div>

              {!canUpdate && (
                <p className="text-sm text-[var(--muted-foreground)]">
                  Tài khoản hiện tại chỉ có quyền xem trạng thái phòng.
                </p>
              )}
            </div>

            {canUpdate && (
              <SheetFooter className="border-t px-6 py-4">
                <Button onClick={() => void handleUpdate()} disabled={isUpdating} className="w-full">
                  {isUpdating && <Loader2 className="animate-spin" />}
                  Chuyển sang {housekeepingLabels[nextHousekeepingStatus[room.housekeepingStatus]]}
                </Button>
              </SheetFooter>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}

function StatusBlock({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2 rounded-lg border p-3">
      <p className="text-xs text-[var(--muted-foreground)]">{label}</p>
      {children}
    </div>
  )
}

function getHousekeepingVariant(status: HousekeepingStatus) {
  if (status === "CLEAN") return "success" as const
  if (status === "DIRTY") return "destructive" as const
  return "warning" as const
}

function getBookingVariant(status: RoomBookingStatus) {
  if (status === "OCCUPIED") return "success" as const
  if (status === "RESERVED") return "confirmed" as const
  return "warning" as const
}
