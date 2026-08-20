"use client"

import * as React from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { format, startOfMonth, endOfMonth, eachDayOfInterval, isSameMonth, isSameDay, isToday, isBefore, startOfToday } from "date-fns"
import { vi } from "date-fns/locale"
import { cn } from "@/lib/utils"

export interface CalendarProps {
  selected?: Date
  onSelect?: (date: Date) => void
  minDate?: Date
  maxDate?: Date
  className?: string
}

export function Calendar({ selected, onSelect, minDate, maxDate, className }: CalendarProps) {
  const [currentMonth, setCurrentMonth] = React.useState(() => selected || new Date())
  const today = startOfToday()

  const days = eachDayOfInterval({
    start: startOfMonth(currentMonth),
    end: endOfMonth(currentMonth),
  })

  const firstDayOfMonth = startOfMonth(currentMonth).getDay()

  const weekdays = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"]

  const goToPreviousMonth = () => {
    setCurrentMonth((prev) => {
      const newDate = new Date(prev)
      newDate.setMonth(newDate.getMonth() - 1)
      return newDate
    })
  }

  const goToNextMonth = () => {
    setCurrentMonth((prev) => {
      const newDate = new Date(prev)
      newDate.setMonth(newDate.getMonth() + 1)
      return newDate
    })
  }

  const isDateDisabled = (date: Date) => {
    if (minDate && isBefore(date, minDate)) return true
    if (maxDate && isBefore(maxDate, date)) return true
    return false
  }

  return (
    <div className={cn("p-3", className)}>
      {/* Month/Year Header */}
      <div className="flex items-center justify-between mb-4">
        <button
          type="button"
          onClick={goToPreviousMonth}
          className="h-7 w-7 flex items-center justify-center rounded-md hover:bg-[var(--muted)] transition-colors"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <span className="text-sm font-semibold">
          {format(currentMonth, "MMMM yyyy", { locale: vi })}
        </span>
        <button
          type="button"
          onClick={goToNextMonth}
          className="h-7 w-7 flex items-center justify-center rounded-md hover:bg-[var(--muted)] transition-colors"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>

      {/* Weekdays */}
      <div className="grid grid-cols-7 mb-2">
        {weekdays.map((day) => (
          <div
            key={day}
            className="h-8 w-9 flex items-center justify-center text-xs font-medium text-[var(--muted-foreground)]"
          >
            {day}
          </div>
        ))}
      </div>

      {/* Days Grid */}
      <div className="grid grid-cols-7 gap-1">
        {/* Empty cells for days before the first day of month */}
        {Array.from({ length: firstDayOfMonth }).map((_, i) => (
          <div key={`empty-${i}`} className="h-9 w-9" />
        ))}

        {/* Days */}
        {days.map((day) => {
          const isSelected = selected && isSameDay(day, selected)
          const isDisabled = isDateDisabled(day)

          return (
            <button
              key={day.toISOString()}
              type="button"
              onClick={() => !isDisabled && onSelect?.(day)}
              disabled={isDisabled}
              className={cn(
                "h-9 w-9 flex items-center justify-center rounded-md text-sm transition-colors",
                isSelected && "bg-[var(--accent)] text-white font-medium",
                !isSelected && isToday(day) && "border border-[var(--accent)] text-[var(--accent)]",
                !isSelected && !isToday(day) && "hover:bg-[var(--muted)]",
                isDisabled && "text-[var(--muted-foreground)] opacity-50 cursor-not-allowed"
              )}
            >
              {format(day, "d")}
            </button>
          )
        })}
      </div>
    </div>
  )
}
