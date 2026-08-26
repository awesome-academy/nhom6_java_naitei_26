"use client"

import { useState } from "react"
import { differenceInCalendarDays, format, parseISO } from "date-fns"
import { CalendarDays, DoorOpen, Loader2, Trash2, Wrench } from "lucide-react"
import { toast } from "sonner"

import { blockTypeLabels, blockTypeStyles } from "@/components/admin/maintenance/block-config"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { deleteRoomStatusBlock, extendRoomStatusBlock } from "@/lib/api/room-status-blocks"
import type { Room, RoomOperationalStatus } from "@/types/room"
import type { RoomStatusBlock } from "@/types/room-status-block"

const operationalLabels: Record<RoomOperationalStatus, string> = {
  ACTIVE: "Hoạt động",
  MAINTENANCE: "Bảo trì",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  RENOVATION: "Cải tạo",
}

interface BlockDetailSheetProps {
  block: RoomStatusBlock
  room: Room | null
  canManage: boolean
  onOpenChange: (open: boolean) => void
  onChanged: () => Promise<void>
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

export function BlockDetailSheet({ block, room, canManage, onOpenChange, onChanged }: BlockDetailSheetProps) {
  const [newEndDate, setNewEndDate] = useState(block.endDate)
  const [mutation, setMutation] = useState<"extend" | "delete" | null>(null)

  const startDate = parseISO(block.startDate)
  const endDate = parseISO(block.endDate)
  const duration = differenceInCalendarDays(endDate, startDate)

  async function handleExtend() {
    const currentBlock = block
    if (newEndDate <= currentBlock.endDate) {
      toast.error("Ngày kết thúc mới phải sau ngày kết thúc hiện tại.")
      return
    }
    setMutation("extend")
    try {
      await extendRoomStatusBlock(currentBlock.publicId, newEndDate)
      await onChanged()
      toast.success("Đã gia hạn lịch bảo trì.")
    } catch (error) {
      toast.error(getErrorMessage(error, "Không thể gia hạn lịch bảo trì."))
    } finally {
      setMutation(null)
    }
  }

  async function handleDelete() {
    const currentBlock = block
    setMutation("delete")
    try {
      await deleteRoomStatusBlock(currentBlock.publicId)
      await onChanged()
      onOpenChange(false)
      toast.success("Đã xóa lịch bảo trì.")
    } catch (error) {
      toast.error(getErrorMessage(error, "Không thể xóa lịch bảo trì."))
    } finally {
      setMutation(null)
    }
  }

  return (
    <Sheet open onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader className="border-b px-6 py-5 pr-12">
          <SheetTitle className="flex items-center gap-2">
            <Wrench className="size-5" /> Chi tiết lịch bảo trì
          </SheetTitle>
          <SheetDescription>Block UUID: {block.publicId}</SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
          <div className="rounded-xl border bg-[var(--muted)]/40 p-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex size-10 items-center justify-center rounded-lg bg-[var(--card)]">
                  <DoorOpen className="size-5" />
                </div>
                <div>
                  <p className="text-lg font-bold">Phòng {block.roomNumber}</p>
                  <p className="text-sm text-[var(--muted-foreground)]">
                    {room?.roomTypeName ?? "Không có thông tin loại phòng"}
                  </p>
                </div>
              </div>
              <Badge variant={block.operationalStatus === "ACTIVE" ? "success" : "destructive"}>
                {operationalLabels[block.operationalStatus]}
              </Badge>
            </div>
          </div>

          <Badge variant="outline" className={blockTypeStyles[block.blockType]}>
            {blockTypeLabels[block.blockType]}
          </Badge>

          <dl className="space-y-4 text-sm">
            <DetailRow label="Ngày bắt đầu" value={format(startDate, "dd/MM/yyyy")} />
            <DetailRow label="Ngày kết thúc" value={`${format(endDate, "dd/MM/yyyy")} (không bao gồm)`} />
            <DetailRow label="Thời lượng" value={`${duration} ngày`} />
            <DetailRow
              label="Tầng"
              value={room?.floor === null || room?.floor === undefined ? "Chưa gán" : String(room.floor)}
            />
          </dl>

          <Separator />

          <section className="space-y-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold">
              <CalendarDays className="size-4" /> Ghi chú
            </h3>
            <p className="whitespace-pre-wrap rounded-lg border p-4 text-sm text-[var(--muted-foreground)]">
              {block.reason || "Không có ghi chú."}
            </p>
          </section>

          {canManage && (
            <>
              <Separator />
              <section className="flex flex-col gap-3">
                <h3 className="text-sm font-semibold">Điều chỉnh lịch</h3>
                <Label htmlFor="maintenance-new-end-date">Gia hạn đến ngày</Label>
                <div className="flex gap-2">
                  <Input
                    id="maintenance-new-end-date"
                    type="date"
                    min={block.endDate}
                    value={newEndDate}
                    onChange={(event) => setNewEndDate(event.target.value)}
                    disabled={mutation !== null}
                  />
                  <Button onClick={() => void handleExtend()} disabled={mutation !== null}>
                    {mutation === "extend" && <Loader2 data-icon="inline-start" className="animate-spin" />}
                    Gia hạn
                  </Button>
                </div>
                <Button variant="destructive" onClick={() => void handleDelete()} disabled={mutation !== null}>
                  {mutation === "delete" ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Trash2 data-icon="inline-start" />}
                  Xóa lịch
                </Button>
              </section>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="text-[var(--muted-foreground)]">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  )
}
