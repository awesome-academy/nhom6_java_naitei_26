"use client"

import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  format,
  isSameDay,
  isWeekend,
  startOfMonth,
} from "date-fns"
import { vi } from "date-fns/locale"
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react"

import {
  findEffectiveOverride,
  formatCompactMoney,
  formatMoney,
} from "@/components/admin/pricing/pricing-utils"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import type { RateOverride } from "@/types/rate-override"
import type { RoomType } from "@/types/room-type"

interface PricingCalendarProps {
  month: Date
  roomTypes: RoomType[]
  activeOverrides: RateOverride[]
  onMonthChange: (month: Date) => void
  onSelectOverride: (override: RateOverride) => void
}

export function PricingCalendar({
  month,
  roomTypes,
  activeOverrides,
  onMonthChange,
  onSelectOverride,
}: PricingCalendarProps) {
  const monthStart = startOfMonth(month)
  const days = eachDayOfInterval({ start: monthStart, end: endOfMonth(monthStart) })

  return (
    <Card>
      <CardContent className="space-y-4 p-0">
        <div className="flex flex-col gap-3 border-b p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" aria-label="Tháng trước" onClick={() => onMonthChange(addMonths(monthStart, -1))}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="outline" onClick={() => onMonthChange(startOfMonth(new Date()))}>
              Hôm nay
            </Button>
            <Button variant="outline" size="icon" aria-label="Tháng sau" onClick={() => onMonthChange(addMonths(monthStart, 1))}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
          <div className="flex items-center gap-2 text-lg font-semibold capitalize">
            <CalendarDays className="h-5 w-5 text-[var(--accent)]" />
            {format(monthStart, "MMMM yyyy", { locale: vi })}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-4 px-4 text-xs text-[var(--muted-foreground)]">
          <span className="flex items-center gap-2"><i className="h-3 w-3 rounded bg-blue-100 ring-1 ring-blue-300" /> Giá override</span>
          <span className="flex items-center gap-2"><i className="h-3 w-3 rounded bg-[var(--muted)]" /> Giá cơ bản</span>
          <span>Rule riêng theo phòng không được tính trong lịch này.</span>
        </div>

        {roomTypes.length === 0 ? (
          <div className="flex min-h-64 items-center justify-center text-sm text-[var(--muted-foreground)]">
            Không có loại phòng phù hợp.
          </div>
        ) : (
          <div className="max-h-[620px] overflow-auto border-t">
            <table className="min-w-max border-separate border-spacing-0 text-sm">
              <thead>
                <tr>
                  <th className="sticky left-0 top-0 z-30 min-w-56 border-b border-r bg-[var(--card)] p-3 text-left">
                    Loại phòng
                  </th>
                  {days.map((day) => (
                    <th
                      key={day.toISOString()}
                      className={cn(
                        "sticky top-0 z-20 min-w-24 border-b border-r bg-[var(--card)] p-2 text-center font-medium",
                        isWeekend(day) && "bg-amber-50",
                        isSameDay(day, new Date()) && "text-[var(--accent)]"
                      )}
                    >
                      <span className="block text-xs capitalize">{format(day, "EEE", { locale: vi })}</span>
                      <span>{format(day, "dd/MM")}</span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {roomTypes.map((roomType) => (
                  <tr key={roomType.code}>
                    <th className="sticky left-0 z-10 border-b border-r bg-[var(--card)] p-3 text-left">
                      <div className="font-semibold">{roomType.name}</div>
                      <div className="mt-1 flex items-center gap-2 text-xs text-[var(--muted-foreground)]">
                        {roomType.code}
                        {!roomType.isActive && <Badge variant="outline">Inactive</Badge>}
                      </div>
                    </th>
                    {days.map((day) => {
                      const date = format(day, "yyyy-MM-dd")
                      const override = findEffectiveOverride(activeOverrides, roomType.code, date)
                      const price = override?.price ?? roomType.basePrice
                      const title = override
                        ? `${override.name} · Priority ${override.priority} · ${formatMoney(price, roomType.currency)}`
                        : `Giá cơ bản · ${formatMoney(price, roomType.currency)}`
                      return (
                        <td
                          key={date}
                          className={cn(
                            "border-b border-r p-1",
                            isWeekend(day) && "bg-amber-50/40"
                          )}
                        >
                          <button
                            type="button"
                            title={title}
                            disabled={!override}
                            onClick={() => override && onSelectOverride(override)}
                            className={cn(
                              "flex h-14 w-full flex-col items-center justify-center rounded-md bg-[var(--muted)] px-1 text-xs font-medium",
                              override && "cursor-pointer bg-blue-100 text-blue-900 ring-1 ring-inset ring-blue-300 hover:bg-blue-200",
                              !override && "cursor-default"
                            )}
                          >
                            <span>{formatCompactMoney(price, roomType.currency)}</span>
                            {override && <span className="mt-1 text-[10px]">P{override.priority}</span>}
                          </button>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
