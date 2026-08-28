"use client"

import { addMonths, format, startOfMonth } from "date-fns"
import { vi } from "date-fns/locale"
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react"

import { Button } from "@/components/ui/button"

interface CalendarToolbarProps {
  month: Date
  onMonthChange: (month: Date) => void
}

export function CalendarToolbar({ month, onMonthChange }: CalendarToolbarProps) {
  const monthStart = startOfMonth(month)

  return (
    <div className="flex flex-col gap-3 border-b bg-card px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3">
        <CalendarDays className="size-5 text-accent" aria-hidden="true" />
        <div className="flex flex-col gap-0.5">
          <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Lịch theo tháng
          </span>
          <span className="text-lg font-semibold capitalize">
            {format(monthStart, "MMMM yyyy", { locale: vi })}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="Tháng trước"
          title="Tháng trước"
          onClick={() => onMonthChange(addMonths(monthStart, -1))}
        >
          <ChevronLeft aria-hidden="true" />
        </Button>
        <Button type="button" variant="outline" onClick={() => onMonthChange(startOfMonth(new Date()))}>
          Hôm nay
        </Button>
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="Tháng sau"
          title="Tháng sau"
          onClick={() => onMonthChange(addMonths(monthStart, 1))}
        >
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </div>
  )
}
