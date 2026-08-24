"use client"

import * as React from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import {
  eachDayOfInterval,
  endOfMonth,
  format,
  getDay,
  isBefore,
  isSameDay,
  isToday,
  isWithinInterval,
  startOfMonth,
} from "date-fns"
import { cn } from "@/lib/utils"

export interface CalendarProps {
  selected?: Date
  selectedRange?: {
    from: Date
    to?: Date
  }
  onSelect?: (date: Date) => void
  minDate?: Date
  maxDate?: Date
  month?: Date
  onMonthChange?: (date: Date) => void
  renderDayContent?: (date: Date, state: {
    disabled: boolean
    ranged: boolean
    selected: boolean
    today: boolean
  }) => React.ReactNode
  cellClassName?: string
  className?: string
}

export function Calendar({
  selected,
  selectedRange,
  onSelect,
  minDate,
  maxDate,
  month,
  onMonthChange,
  renderDayContent,
  cellClassName,
  className,
}: CalendarProps) {
  const [currentMonth, setCurrentMonth] = React.useState(() => selected || new Date())
  const displayedMonth = month ?? currentMonth

  const days = eachDayOfInterval({
    start: startOfMonth(displayedMonth),
    end: endOfMonth(displayedMonth),
  })

  const firstDayOfMonth = (getDay(startOfMonth(displayedMonth)) + 6) % 7

  const weekdays = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]

  const setMonth = React.useCallback((date: Date) => {
    if (onMonthChange) {
      onMonthChange(date)
      return
    }
    setCurrentMonth(date)
  }, [onMonthChange])

  const goToPreviousMonth = () => {
    const newDate = new Date(displayedMonth)
    newDate.setMonth(newDate.getMonth() - 1)
    setMonth(newDate)
  }

  const goToNextMonth = () => {
    const newDate = new Date(displayedMonth)
    newDate.setMonth(newDate.getMonth() + 1)
    setMonth(newDate)
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
          {format(displayedMonth, "MM/yyyy")}
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
            className={cn(
              "flex h-8 w-9 items-center justify-center text-xs font-medium text-[var(--muted-foreground)]",
              cellClassName && "w-14"
            )}
          >
            {day}
          </div>
        ))}
      </div>

      {/* Days Grid */}
      <div className="grid grid-cols-7 gap-1">
        {/* Empty cells for days before the first day of month */}
        {Array.from({ length: firstDayOfMonth }).map((_, i) => (
          <div key={`empty-${i}`} className={cn("h-9 w-9", cellClassName)} />
        ))}

        {/* Days */}
        {days.map((day) => {
          const isSelected = Boolean(
            selected && isSameDay(day, selected) ||
            selectedRange?.from && isSameDay(day, selectedRange.from) ||
            selectedRange?.to && isSameDay(day, selectedRange.to)
          )
          const isDisabled = isDateDisabled(day)

          return (
            <button
              key={day.toISOString()}
              type="button"
              onClick={() => !isDisabled && onSelect?.(day)}
              disabled={isDisabled}
              className={cn(
                "flex h-9 w-9 items-center justify-center rounded-md text-sm transition-colors",
                cellClassName,
                selectedRange?.to && isWithinInterval(day, {
                  start: selectedRange.from,
                  end: selectedRange.to,
                }) && "bg-[var(--accent)]/10",
                isSelected && "bg-[var(--accent)] text-white font-medium",
                !isSelected && isToday(day) && "border border-[var(--accent)] text-[var(--accent)]",
                !isSelected && !isToday(day) && "hover:bg-[var(--muted)]",
                isDisabled && "text-[var(--muted-foreground)] opacity-50 cursor-not-allowed"
              )}
            >
              {renderDayContent
                ? renderDayContent(day, {
                  disabled: isDisabled,
                  ranged: Boolean(selectedRange?.to && isWithinInterval(day, {
                    start: selectedRange.from,
                    end: selectedRange.to,
                  })),
                  selected: Boolean(isSelected),
                  today: isToday(day),
                })
                : format(day, "d")}
            </button>
          )
        })}
      </div>
    </div>
  )
}
