import { addDays, getISODay, isBefore, parseISO } from "date-fns"

import type { RateOverride } from "@/types/rate-override"

export const weekdayOptions = [
  { value: 1, shortLabel: "T2", label: "Thứ Hai" },
  { value: 2, shortLabel: "T3", label: "Thứ Ba" },
  { value: 3, shortLabel: "T4", label: "Thứ Tư" },
  { value: 4, shortLabel: "T5", label: "Thứ Năm" },
  { value: 5, shortLabel: "T6", label: "Thứ Sáu" },
  { value: 6, shortLabel: "T7", label: "Thứ Bảy" },
  { value: 7, shortLabel: "CN", label: "Chủ Nhật" },
] as const

export function formatMoney(value: number, currency = "VND"): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(value)
}

export function formatCompactMoney(value: number, currency = "VND"): string {
  return new Intl.NumberFormat("vi-VN", {
    notation: "compact",
    maximumFractionDigits: 1,
    style: "currency",
    currency,
  }).format(value)
}

export function formatWeekdays(weekdays: number[] | null): string {
  if (weekdays === null || weekdays.length === 7) return "Mọi ngày"
  return weekdays
    .map((value) => weekdayOptions.find((option) => option.value === value)?.shortLabel)
    .filter(Boolean)
    .join(", ")
}

export function appliesOnDate(rule: RateOverride, date: string): boolean {
  if (date < rule.startDate || date >= rule.endDate) return false
  return rule.weekdays === null || rule.weekdays.includes(getISODay(parseISO(date)))
}

export function findEffectiveOverride(
  overrides: RateOverride[],
  roomTypeCode: string,
  date: string
): RateOverride | null {
  return overrides
    .filter(
      (rule) => rule.roomTypeCode === roomTypeCode && appliesOnDate(rule, date)
    )
    .sort((left, right) => right.priority - left.priority || left.id - right.id)[0] ?? null
}

export function hasApplicableDateConflict(
  candidate: {
    roomTypeCode: string
    startDate: string
    endDate: string
    weekdays: number[] | null
    priority: number
  },
  existing: RateOverride
): boolean {
  if (
    existing.roomTypeCode !== candidate.roomTypeCode ||
    existing.priority !== candidate.priority ||
    existing.startDate >= candidate.endDate ||
    existing.endDate <= candidate.startDate
  ) {
    return false
  }

  const overlapStart = candidate.startDate > existing.startDate
    ? candidate.startDate
    : existing.startDate
  const overlapEnd = candidate.endDate < existing.endDate
    ? candidate.endDate
    : existing.endDate
  const candidateWeekdays = candidate.weekdays ?? weekdayOptions.map((day) => day.value)
  const existingWeekdays = existing.weekdays ?? weekdayOptions.map((day) => day.value)
  const sharedWeekdays = new Set(
    candidateWeekdays.filter((weekday) => existingWeekdays.includes(weekday))
  )

  if (sharedWeekdays.size === 0) return false
  const end = parseISO(overlapEnd)
  for (let date = parseISO(overlapStart), checked = 0;
    isBefore(date, end) && checked < 7;
    date = addDays(date, 1), checked += 1) {
    if (sharedWeekdays.has(getISODay(date))) return true
  }
  return false
}
