"use client"

import {
  eachDayOfInterval,
  endOfMonth,
  format,
  isSameDay,
  isWeekend,
  startOfMonth,
} from "date-fns"
import { vi } from "date-fns/locale"

import { CalendarToolbar } from "@/components/admin/calendar-toolbar"
import {
  findEffectiveOverride,
  formatCompactMoney,
  formatMoney,
  formatRateChange,
  getRateCellStyle,
} from "@/components/admin/pricing/pricing-utils"
import { Badge } from "@/components/ui/badge"
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
    <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b px-4 py-3 text-xs text-muted-foreground">
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-muted/50" /> Giá base
        </span>
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-sky-100 ring-1 ring-sky-300" /> Override không tăng
        </span>
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-blue-100" /> Tăng 0–10%
        </span>
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-blue-200" /> Tăng 10–25%
        </span>
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-blue-300" /> Tăng 25–50%
        </span>
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-blue-500" /> Tăng 50–100%
        </span>
        <span className="flex items-center gap-2">
          <span className="size-3 rounded bg-blue-700" /> Tăng trên 100%
        </span>
      </div>
      <CalendarToolbar month={month} onMonthChange={onMonthChange} />

      {roomTypes.length === 0 ? (
        <div className="flex min-h-64 items-center justify-center text-sm text-muted-foreground">
          Không có loại phòng phù hợp.
        </div>
      ) : (
        <div className="max-h-[620px] overflow-auto">
          <table className="min-w-max border-separate border-spacing-0 text-sm">
            <thead>
              <tr>
                <th className="sticky left-0 top-0 z-30 min-w-56 border-b border-r bg-card p-3 text-left">
                  Loại phòng
                </th>
                {days.map((day) => (
                  <th
                    key={day.toISOString()}
                    className={cn(
                      "sticky top-0 z-20 h-14 min-w-24 border-b border-r bg-card px-1 text-center font-medium",
                      isWeekend(day) && "bg-slate-50",
                      isSameDay(day, new Date()) && "bg-blue-50 text-[var(--accent)]"
                    )}
                  >
                    <span className="block text-[10px] uppercase text-muted-foreground">
                      {format(day, "EEE", { locale: vi })}
                    </span>
                    <span className={cn(
                      "mt-0.5 inline-flex size-7 items-center justify-center rounded-full",
                      isSameDay(day, new Date()) && "bg-[var(--accent)] text-white"
                    )}>
                      {format(day, "d")}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {roomTypes.map((roomType) => (
                <tr key={roomType.code}>
                  <th className="sticky left-0 z-10 border-b border-r bg-card p-3 text-left">
                    <div className="font-semibold">{roomType.name}</div>
                    <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                      {roomType.code}
                      {!roomType.isActive && <Badge variant="outline">Inactive</Badge>}
                    </div>
                  </th>
                  {days.map((day) => {
                    const date = format(day, "yyyy-MM-dd")
                    const override = findEffectiveOverride(activeOverrides, roomType.code, date)
                    const price = override?.price ?? roomType.basePrice
                    const basePrice = Number(roomType.basePrice)
                    const title = override
                      ? `${override.name} · Priority ${override.priority} · ${formatMoney(price, roomType.currency)} · ${formatRateChange(price, basePrice)} so với giá base ${formatMoney(basePrice, roomType.currency)}`
                      : `Giá cơ bản · ${formatMoney(price, roomType.currency)}`
                    return (
                      <td
                        key={date}
                        className={cn(
                          "h-14 min-w-24 border-b border-r p-0.5",
                          isWeekend(day) && "bg-slate-50/70",
                          isSameDay(day, new Date()) && "bg-blue-50/70"
                        )}
                      >
                        <button
                          type="button"
                          title={title}
                          disabled={!override}
                          onClick={() => override && onSelectOverride(override)}
                          className={cn(
                            "flex h-14 w-full flex-col items-center justify-center rounded-md px-1 text-xs font-medium transition-colors",
                            getRateCellStyle(price, basePrice, Boolean(override)),
                            override && "cursor-pointer hover:brightness-95",
                            !override && "cursor-default"
                          )}
                        >
                          <span className="font-semibold">{formatCompactMoney(price, roomType.currency)}</span>
                          <span className="text-[10px]">
                            {override ? `${formatRateChange(price, basePrice)} · P${override.priority}` : "Base"}
                          </span>
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
    </div>
  )
}
