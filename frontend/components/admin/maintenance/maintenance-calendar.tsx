import {
  addDays,
  eachDayOfInterval,
  endOfMonth,
  format,
  isSameDay,
  isToday,
  isWeekend,
  startOfMonth,
} from "date-fns"
import { vi } from "date-fns/locale"
import { Building2, Wrench } from "lucide-react"

import {
  blockTypeDotStyles,
  blockTypeLabels,
  blockTypeStyles,
} from "@/components/admin/maintenance/block-config"
import { Card, CardContent } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import type { Room } from "@/types/room"
import type { RoomBlockType, RoomStatusBlock } from "@/types/room-status-block"

interface MaintenanceCalendarProps {
  month: Date
  rooms: Room[]
  blocks: RoomStatusBlock[]
  canCreate: boolean
  onSelectEmptyCell: (room: Room, date: Date) => void
  onSelectBlock: (block: RoomStatusBlock) => void
}

export function MaintenanceCalendar({
  month,
  rooms,
  blocks,
  canCreate,
  onSelectEmptyCell,
  onSelectBlock,
}: MaintenanceCalendarProps) {
  const monthStart = startOfMonth(month)
  const monthEnd = endOfMonth(month)
  const days = eachDayOfInterval({ start: monthStart, end: monthEnd })
  const monthStartKey = format(monthStart, "yyyy-MM-dd")

  const blocksByRoom = blocks.reduce<Map<string, RoomStatusBlock[]>>((groups, block) => {
    const current = groups.get(block.roomNumber) ?? []
    current.push(block)
    groups.set(block.roomNumber, current)
    return groups
  }, new Map())

  if (rooms.length === 0) {
    return (
      <Card>
        <CardContent className="flex min-h-72 flex-col items-center justify-center gap-3 text-center">
          <Building2 className="h-10 w-10 text-[var(--muted-foreground)]" />
          <p className="font-medium">Không có phòng phù hợp với bộ lọc</p>
          <p className="text-sm text-[var(--muted-foreground)]">
            Thử bỏ bớt điều kiện để xem lịch bảo trì.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="min-w-0 space-y-4">
      <BlockLegend />
      <div className="max-h-[68vh] w-full max-w-full overflow-auto rounded-xl border bg-[var(--card)] shadow-sm">
        <table className="min-w-max border-separate border-spacing-0 text-sm">
          <thead className="sticky top-0 z-30 bg-[var(--card)] shadow-sm">
            <tr>
              <th className="sticky left-0 z-40 min-w-56 border-b border-r bg-[var(--card)] px-4 py-3 text-left font-semibold">
                Phòng
              </th>
              {days.map((day) => (
                <th
                  key={day.toISOString()}
                  className={cn(
                    "h-14 min-w-12 border-b border-r px-1 text-center font-medium",
                    isWeekend(day) && "bg-slate-50",
                    isToday(day) && "bg-blue-50 text-[var(--accent)]"
                  )}
                >
                  <span className="block text-[10px] uppercase text-[var(--muted-foreground)]">
                    {format(day, "EEE", { locale: vi })}
                  </span>
                  <span className={cn("mt-0.5 inline-flex h-7 w-7 items-center justify-center rounded-full", isToday(day) && "bg-[var(--accent)] text-white")}>
                    {format(day, "d")}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rooms.map((room) => {
              const roomBlocks = blocksByRoom.get(room.roomNumber) ?? []
              return (
                <tr key={room.roomNumber} className="group">
                  <th className="sticky left-0 z-20 min-w-56 border-b border-r bg-[var(--card)] px-4 py-3 text-left group-hover:bg-[var(--muted)]/40">
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-semibold">{room.roomNumber}</p>
                        <p className="truncate text-xs font-normal text-[var(--muted-foreground)]">
                          {room.roomTypeName} · {room.floor === null ? "Chưa gán tầng" : `Tầng ${room.floor}`}
                        </p>
                      </div>
                      {room.operationalStatus !== "ACTIVE" && (
                        <Wrench className="h-4 w-4 shrink-0 text-[var(--destructive)]" aria-label="Phòng không hoạt động" />
                      )}
                    </div>
                  </th>
                  {days.map((day) => {
                    const dateKey = format(day, "yyyy-MM-dd")
                    const block = roomBlocks.find(
                      (item) => item.startDate <= dateKey && item.endDate > dateKey
                    )
                    return (
                      <CalendarCell
                        key={dateKey}
                        day={day}
                        dateKey={dateKey}
                        monthStartKey={monthStartKey}
                        room={room}
                        block={block}
                        canCreate={canCreate}
                        onSelectEmptyCell={onSelectEmptyCell}
                        onSelectBlock={onSelectBlock}
                      />
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

interface CalendarCellProps {
  day: Date
  dateKey: string
  monthStartKey: string
  room: Room
  block?: RoomStatusBlock
  canCreate: boolean
  onSelectEmptyCell: (room: Room, date: Date) => void
  onSelectBlock: (block: RoomStatusBlock) => void
}

function CalendarCell({
  day,
  dateKey,
  monthStartKey,
  room,
  block,
  canCreate,
  onSelectEmptyCell,
  onSelectBlock,
}: CalendarCellProps) {
  const nextDateKey = format(addDays(day, 1), "yyyy-MM-dd")

  if (block) {
    const firstVisibleDate = block.startDate < monthStartKey ? monthStartKey : block.startDate
    const isFirstCell = dateKey === firstVisibleDate
    const isLastCell = nextDateKey >= block.endDate || isSameDay(day, endOfMonth(day))
    return (
      <td className={cn("h-14 min-w-12 border-b border-r p-0.5", isWeekend(day) && "bg-slate-50/70", isToday(day) && "bg-blue-50/70")}>
        <button
          type="button"
          onClick={() => onSelectBlock(block)}
          title={`${blockTypeLabels[block.blockType]} · ${block.startDate} → ${block.endDate}`}
          className={cn(
            "flex h-12 w-full items-center overflow-hidden border-y px-1 text-left text-[10px] font-medium transition-opacity hover:opacity-80 focus:relative focus:z-10 focus:outline-none focus:ring-2 focus:ring-[var(--ring)]",
            blockTypeStyles[block.blockType],
            isFirstCell && "rounded-l-md border-l",
            isLastCell && "rounded-r-md border-r",
            !isFirstCell && "border-l-0",
            !isLastCell && "border-r-0"
          )}
        >
          {isFirstCell && <span className="truncate">{blockTypeLabels[block.blockType]}</span>}
          <span className="sr-only">
            {blockTypeLabels[block.blockType]} phòng {room.roomNumber}, từ {block.startDate} đến {block.endDate}
          </span>
        </button>
      </td>
    )
  }

  return (
    <td className={cn("h-14 min-w-12 border-b border-r p-0.5", isWeekend(day) && "bg-slate-50/70", isToday(day) && "bg-blue-50/70")}>
      <button
        type="button"
        disabled={!canCreate}
        onClick={() => onSelectEmptyCell(room, day)}
        aria-label={`Tạo lịch cho phòng ${room.roomNumber} ngày ${dateKey}`}
        className="h-12 w-full rounded-md transition-colors hover:bg-[var(--muted)] focus:outline-none focus:ring-2 focus:ring-[var(--ring)] disabled:cursor-default disabled:hover:bg-transparent"
      />
    </td>
  )
}

function BlockLegend() {
  return (
    <div className="flex flex-wrap gap-x-5 gap-y-2 rounded-xl border bg-[var(--card)] p-4 text-sm">
      {(Object.keys(blockTypeLabels) as RoomBlockType[]).map((blockType) => (
        <div key={blockType} className="flex items-center gap-2">
          <span className={cn("h-3 w-3 rounded-full", blockTypeDotStyles[blockType])} />
          <span>{blockTypeLabels[blockType]}</span>
        </div>
      ))}
    </div>
  )
}
