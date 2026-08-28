import {
  eachDayOfInterval,
  endOfMonth,
  format,
  isToday,
  isWeekend,
  startOfMonth,
} from "date-fns"
import { vi } from "date-fns/locale"
import type { ReactNode } from "react"
import { Building2, Wrench } from "lucide-react"

import { CalendarToolbar } from "@/components/admin/calendar-toolbar"
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
  onMonthChange: (month: Date) => void
  onSelectEmptyCell: (room: Room, date: Date) => void
  onSelectBlock: (block: RoomStatusBlock) => void
}

export function MaintenanceCalendar({
  month,
  rooms,
  blocks,
  canCreate,
  onMonthChange,
  onSelectEmptyCell,
  onSelectBlock,
}: MaintenanceCalendarProps) {
  const monthStart = startOfMonth(month)
  const monthEnd = endOfMonth(month)
  const days = eachDayOfInterval({ start: monthStart, end: monthEnd })

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
    <div className="flex min-w-0 flex-col gap-4">
      <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
        <div className="border-b px-4 py-3">
          <BlockLegend />
        </div>
        <CalendarToolbar month={month} onMonthChange={onMonthChange} />
        <div className="max-h-[68vh] w-full max-w-full overflow-auto">
          <table className="min-w-max border-separate border-spacing-0 text-sm">
            <thead className="sticky top-0 z-30 bg-card shadow-sm">
            <tr>
              <th className="sticky left-0 z-40 min-w-56 border-b border-r bg-card px-4 py-3 text-left font-semibold">
                Phòng
              </th>
              {days.map((day) => (
                <th
                  key={day.toISOString()}
                  className={cn(
                    "h-14 min-w-24 border-b border-r px-1 text-center font-medium",
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
                    <th className="sticky left-0 z-20 min-w-56 border-b border-r bg-card px-4 py-3 text-left group-hover:bg-muted/40">
                      <div className="flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="font-semibold">{room.roomNumber}</p>
                          <p className="truncate text-xs font-normal text-muted-foreground">
                            {room.roomTypeName} · {room.floor === null ? "Chưa gán tầng" : `Tầng ${room.floor}`}
                          </p>
                        </div>
                        {room.operationalStatus !== "ACTIVE" && (
                          <Wrench className="size-4 shrink-0 text-destructive" aria-label="Phòng không hoạt động" />
                        )}
                      </div>
                    </th>
                    <RoomCalendarCells
                      days={days}
                      room={room}
                      roomBlocks={roomBlocks}
                      canCreate={canCreate}
                      onSelectEmptyCell={onSelectEmptyCell}
                      onSelectBlock={onSelectBlock}
                    />
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

interface RoomCalendarCellsProps {
  days: Date[]
  room: Room
  roomBlocks: RoomStatusBlock[]
  canCreate: boolean
  onSelectEmptyCell: (room: Room, date: Date) => void
  onSelectBlock: (block: RoomStatusBlock) => void
}

function RoomCalendarCells({
  days,
  room,
  roomBlocks,
  canCreate,
  onSelectEmptyCell,
  onSelectBlock,
}: RoomCalendarCellsProps) {
  const cells: ReactNode[] = []

  for (let index = 0; index < days.length;) {
    const day = days[index]
    const dateKey = format(day, "yyyy-MM-dd")
    const block = findBlockForDate(roomBlocks, dateKey)

    if (!block) {
      cells.push(
        <CalendarCell
          key={dateKey}
          day={day}
          dateKey={dateKey}
          room={room}
          canCreate={canCreate}
          onSelectEmptyCell={onSelectEmptyCell}
        />
      )
      index += 1
      continue
    }

    let span = 1
    while (index + span < days.length) {
      const nextDateKey = format(days[index + span], "yyyy-MM-dd")
      const nextBlock = findBlockForDate(roomBlocks, nextDateKey)
      if (nextBlock?.publicId !== block.publicId) break
      span += 1
    }

    cells.push(
      <td key={`${block.publicId}-${dateKey}`} colSpan={span} className="h-14 border-b border-r p-0.5">
        <button
          type="button"
          onClick={() => onSelectBlock(block)}
          title={`${blockTypeLabels[block.blockType]} · ${block.startDate} → ${block.endDate}`}
          className={cn(
            "flex h-12 w-full items-center justify-center overflow-hidden rounded-md border px-3 text-center text-xs font-medium transition-opacity hover:opacity-80 focus:relative focus:z-10 focus:outline-none focus:ring-2 focus:ring-ring",
            blockTypeStyles[block.blockType]
          )}
        >
          <span className="min-w-0 max-w-full truncate text-center">{blockTypeLabels[block.blockType]}</span>
          <span className="sr-only">
            {blockTypeLabels[block.blockType]} phòng {room.roomNumber}, từ {block.startDate} đến {block.endDate}
          </span>
        </button>
      </td>
    )
    index += span
  }

  return cells
}

function findBlockForDate(roomBlocks: RoomStatusBlock[], dateKey: string) {
  return roomBlocks.find((block) => block.startDate <= dateKey && block.endDate > dateKey)
}

interface CalendarCellProps {
  day: Date
  dateKey: string
  room: Room
  canCreate: boolean
  onSelectEmptyCell: (room: Room, date: Date) => void
}

function CalendarCell({
  day,
  dateKey,
  room,
  canCreate,
  onSelectEmptyCell,
}: CalendarCellProps) {
  return (
    <td className={cn("h-14 min-w-24 border-b border-r p-0.5", isWeekend(day) && "bg-slate-50/70", isToday(day) && "bg-blue-50/70")}>
      <button
        type="button"
        disabled={!canCreate}
        onClick={() => onSelectEmptyCell(room, day)}
        aria-label={`Tạo lịch cho phòng ${room.roomNumber} ngày ${dateKey}`}
        className="h-12 w-full rounded-md transition-colors hover:bg-muted focus:outline-none focus:ring-2 focus:ring-ring disabled:cursor-default disabled:hover:bg-transparent"
      />
    </td>
  )
}

function BlockLegend() {
  return (
    <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-muted-foreground">
      {(Object.keys(blockTypeLabels) as RoomBlockType[]).map((blockType) => (
        <div key={blockType} className="flex items-center gap-2">
          <span className={cn("size-3 rounded-full", blockTypeDotStyles[blockType])} />
          <span>{blockTypeLabels[blockType]}</span>
        </div>
      ))}
    </div>
  )
}
