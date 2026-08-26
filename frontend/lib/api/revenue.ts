import { apiClient } from "@/lib/api/client"
import type {
  DailyRevenuePoint,
  MonthlyRevenuePoint,
  OccupancyMetrics,
  RevenueRange,
  RoomTypeRevenueBreakdown,
  SourceRevenueBreakdown,
} from "@/types/revenue"

function revenuePath(path: string, range: RevenueRange, extra?: Record<string, string>): string {
  const params = new URLSearchParams({ from: range.from, to: range.to, ...extra })
  return `/api/revenue/${path}?${params.toString()}`
}

export function getOccupancyMetrics(range: RevenueRange): Promise<OccupancyMetrics> {
  return apiClient.get<OccupancyMetrics>(revenuePath("occupancy", range))
}

export function getDailyRevenue(range: RevenueRange): Promise<DailyRevenuePoint[]> {
  return apiClient.get<DailyRevenuePoint[]>(revenuePath("daily", range))
}

export function getMonthlyRevenue(range: RevenueRange): Promise<MonthlyRevenuePoint[]> {
  return apiClient.get<MonthlyRevenuePoint[]>(revenuePath("monthly", range))
}

export function getRevenueBySource(range: RevenueRange): Promise<SourceRevenueBreakdown[]> {
  return apiClient.get<SourceRevenueBreakdown[]>(revenuePath("by-source", range))
}

export function getRevenueByRoomType(
  range: RevenueRange,
  limit = 10
): Promise<RoomTypeRevenueBreakdown[]> {
  return apiClient.get<RoomTypeRevenueBreakdown[]>(
    revenuePath("by-room-type", range, { limit: String(limit) })
  )
}
