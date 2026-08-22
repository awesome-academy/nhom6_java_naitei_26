"use client"

import * as React from "react"
import { format, parse, isValid } from "date-fns"
import { vi } from "date-fns/locale"
import { CalendarDays } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"

interface DatePickerDobProps {
  value: string // yyyy-MM-dd format
  onChange: (value: string) => void
  maxDate?: Date
  minDate?: Date
  error?: boolean
}

const MONTHS_VI = [
  "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4",
  "Tháng 5", "Tháng 6", "Tháng 7", "Tháng 8",
  "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12",
]

const DEFAULT_DATE = "2000-01-01"

export function DatePickerDob({
  value,
  onChange,
  maxDate = new Date(),
  minDate,
  error = false,
}: DatePickerDobProps) {
  const [open, setOpen] = React.useState(false)
  const [dropdownType, setDropdownType] = React.useState<"month" | "year" | null>(null)

  // Parse current value or use default
  const displayDate = value || DEFAULT_DATE
  const parsedDate = parse(displayDate, "yyyy-MM-dd", new Date())
  const isValidDate = parsedDate && isValid(parsedDate)

  // Calendar state - default to 2000-01-01
  const [viewDate, setViewDate] = React.useState(() => {
    if (value) {
      const date = parse(value, "yyyy-MM-dd", new Date())
      if (isValid(date)) return date
    }
    return new Date(2000, 0, 1)
  })

  // Year range
  const currentYear = new Date().getFullYear()
  const years = Array.from({ length: currentYear - 1929 }, (_, i) => 1930 + i)

  const handleDayClick = (day: number) => {
    const newDate = new Date(viewDate.getFullYear(), viewDate.getMonth(), day)
    const formatted = format(newDate, "yyyy-MM-dd")
    onChange(formatted)
    setOpen(false)
    setDropdownType(null)
  }

  const handleMonthSelect = (month: number) => {
    setViewDate(new Date(viewDate.getFullYear(), month, viewDate.getDate()))
    setDropdownType(null)
  }

  const handleYearSelect = (year: number) => {
    setViewDate(new Date(year, viewDate.getMonth(), viewDate.getDate()))
    setDropdownType(null)
  }

  const isSelected = (day: number) => {
    if (!isValidDate) return false
    return (
      parsedDate.getDate() === day &&
      parsedDate.getMonth() === viewDate.getMonth() &&
      parsedDate.getFullYear() === viewDate.getFullYear()
    )
  }

  const isDisabled = (day: number) => {
    const date = new Date(viewDate.getFullYear(), viewDate.getMonth(), day)
    if (maxDate && date > maxDate) return true
    if (minDate && date < minDate) return true
    return false
  }

  // Calendar helpers
  const getDaysInMonth = (date: Date) => {
    return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
  }

  const getFirstDayOfMonth = (date: Date) => {
    return new Date(date.getFullYear(), date.getMonth(), 1).getDay()
  }

  // Generate calendar days
  const daysInMonth = getDaysInMonth(viewDate)
  const firstDayOfMonth = getFirstDayOfMonth(viewDate)
  const days: (number | null)[] = []
  for (let i = 0; i < firstDayOfMonth; i++) {
    days.push(null)
  }
  for (let i = 1; i <= daysInMonth; i++) {
    days.push(i)
  }

  // Format display value
  const displayValue = isValidDate
    ? format(parsedDate, "dd/MM/yyyy", { locale: vi })
    : "01/01/2000"

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className={cn(
            "h-12 w-full justify-start text-left font-normal px-3",
            !value && "text-muted-foreground"
          )}
          type="button"
        >
          {displayValue}
          <CalendarDays className="ml-auto h-5 w-5 text-muted-foreground" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <div className="p-3">
          {/* Month/Year Header - Clickable */}
          <div className="flex items-center justify-center gap-4 mb-3 relative">
            <button
              type="button"
              onClick={() => setDropdownType(dropdownType === "month" ? null : "month")}
              className={cn(
                "text-sm font-medium transition-colors cursor-pointer",
                dropdownType === "month" ? "text-[var(--accent)]" : "text-foreground hover:text-[var(--accent)]"
              )}
            >
              {MONTHS_VI[viewDate.getMonth()]}
            </button>
            <button
              type="button"
              onClick={() => setDropdownType(dropdownType === "year" ? null : "year")}
              className={cn(
                "text-sm font-medium transition-colors cursor-pointer",
                dropdownType === "year" ? "text-[var(--accent)]" : "text-foreground hover:text-[var(--accent)]"
              )}
            >
              {viewDate.getFullYear()}
            </button>
          </div>

          {/* Month Dropdown */}
          {dropdownType === "month" && (
            <div className="grid grid-cols-4 gap-1 mb-3 max-h-40 overflow-y-auto">
              {MONTHS_VI.map((month, index) => (
                <button
                  key={month}
                  type="button"
                  onClick={() => handleMonthSelect(index)}
                  className={cn(
                    "h-8 rounded text-xs transition-colors",
                    viewDate.getMonth() === index && "bg-[var(--accent)] text-white",
                    viewDate.getMonth() !== index && "hover:bg-[var(--muted)]"
                  )}
                >
                  {month.replace("Tháng ", "")}
                </button>
              ))}
            </div>
          )}

          {/* Year Dropdown */}
          {dropdownType === "year" && (
            <div className="max-h-40 overflow-y-auto mb-3">
              <div className="grid grid-cols-4 gap-1">
                {years.map((year) => (
                  <button
                    key={year}
                    type="button"
                    onClick={() => handleYearSelect(year)}
                    className={cn(
                      "h-8 rounded text-xs transition-colors",
                      viewDate.getFullYear() === year && "bg-[var(--accent)] text-white",
                      viewDate.getFullYear() !== year && "hover:bg-[var(--muted)]"
                    )}
                  >
                    {year}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Calendar */}
          <div>
            {/* Day Headers */}
            <div className="grid grid-cols-7 gap-1 mb-2">
              {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((day) => (
                <div
                  key={day}
                  className="h-8 w-9 flex items-center justify-center text-xs text-muted-foreground"
                >
                  {day}
                </div>
              ))}
            </div>

            {/* Calendar Days */}
            <div className="grid grid-cols-7 gap-1">
              {days.map((day, index) => (
                <div key={index} className="h-8 w-9 p-0">
                  {day !== null && (
                    <button
                      type="button"
                      className={cn(
                        "h-8 w-9 flex items-center justify-center rounded-md text-sm transition-colors",
                        isSelected(day) && "bg-[var(--accent)] text-white font-medium hover:bg-[var(--accent)]",
                        !isSelected(day) && "hover:bg-[var(--muted)]",
                        isDisabled(day) && "text-muted-foreground opacity-50 cursor-not-allowed hover:bg-transparent"
                      )}
                      onClick={() => !isDisabled(day) && handleDayClick(day)}
                      disabled={isDisabled(day)}
                    >
                      {day}
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}
