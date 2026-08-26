"use client"

import { format, parseISO } from "date-fns"
import { vi } from "date-fns/locale"

import {
  formatMoney,
  formatWeekdays,
} from "@/components/admin/pricing/pricing-utils"
import { Badge } from "@/components/ui/badge"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import type { RateOverride } from "@/types/rate-override"

interface RateOverrideDetailSheetProps {
  override: RateOverride | null
  onOpenChange: (open: boolean) => void
}

export function RateOverrideDetailSheet({
  override,
  onOpenChange,
}: RateOverrideDetailSheetProps) {
  return (
    <Sheet open={override !== null} onOpenChange={onOpenChange}>
      <SheetContent>
        {override && (
          <div className="flex h-full flex-col">
            <SheetHeader className="border-b p-6 pr-12">
              <div className="flex items-center gap-2">
                <Badge>Priority {override.priority}</Badge>
                <Badge variant="outline">Đang áp dụng</Badge>
              </div>
              <SheetTitle>{override.name}</SheetTitle>
              <SheetDescription>
                Chi tiết rule đang tạo nên giá hiển thị trên lịch.
              </SheetDescription>
            </SheetHeader>

            <dl className="grid gap-5 p-6 text-sm">
              <Detail label="Loại phòng" value={`${override.roomTypeCode} · ${override.roomTypeName}`} />
              <Detail
                label="Khoảng ngày"
                value={`${format(parseISO(override.startDate), "dd/MM/yyyy", { locale: vi })} – ${format(parseISO(override.endDate), "dd/MM/yyyy", { locale: vi })} (không gồm ngày cuối)`}
              />
              <Detail label="Ngày áp dụng" value={formatWeekdays(override.weekdays)} />
              <Detail label="Giá" value={formatMoney(override.price)} />
            </dl>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1">
      <dt className="text-xs font-medium uppercase tracking-wide text-[var(--muted-foreground)]">
        {label}
      </dt>
      <dd className="font-medium">{value}</dd>
    </div>
  )
}
