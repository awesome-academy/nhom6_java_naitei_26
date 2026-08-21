import { differenceInCalendarDays, format, parseISO } from "date-fns"
import { CalendarDays, DoorOpen, Wrench } from "lucide-react"

import { blockTypeLabels, blockTypeStyles } from "@/components/admin/maintenance/block-config"
import { Badge } from "@/components/ui/status-badge"
import { Separator } from "@/components/ui/separator"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import type { Room, RoomOperationalStatus } from "@/types/room"
import type { RoomStatusBlock } from "@/types/room-status-block"

const operationalLabels: Record<RoomOperationalStatus, string> = {
  ACTIVE: "Hoạt động",
  MAINTENANCE: "Bảo trì",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  RENOVATION: "Cải tạo",
}

interface BlockDetailSheetProps {
  block: RoomStatusBlock | null
  room: Room | null
  onOpenChange: (open: boolean) => void
}

export function BlockDetailSheet({ block, room, onOpenChange }: BlockDetailSheetProps) {
  if (!block) {
    return <Sheet open={false} onOpenChange={onOpenChange} />
  }

  const startDate = parseISO(block.startDate)
  const endDate = parseISO(block.endDate)
  const duration = differenceInCalendarDays(endDate, startDate)

  return (
    <Sheet open onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader className="border-b px-6 py-5 pr-12">
          <SheetTitle className="flex items-center gap-2">
            <Wrench className="h-5 w-5" /> Chi tiết lịch bảo trì
          </SheetTitle>
          <SheetDescription>Block UUID: {block.publicId}</SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
          <div className="rounded-xl border bg-[var(--muted)]/40 p-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[var(--card)]">
                  <DoorOpen className="h-5 w-5" />
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
              <CalendarDays className="h-4 w-4" /> Ghi chú
            </h3>
            <p className="whitespace-pre-wrap rounded-lg border p-4 text-sm text-[var(--muted-foreground)]">
              {block.reason || "Không có ghi chú."}
            </p>
          </section>
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
