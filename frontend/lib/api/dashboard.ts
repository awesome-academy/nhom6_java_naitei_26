import { apiClient } from "./client"
import type { DashboardOverview } from "@/types/dashboard"

const ENDPOINT = "/api/admin/dashboard"

export function getDashboardOverview(date?: string): Promise<DashboardOverview> {
  const query = date ? `?date=${encodeURIComponent(date)}` : ""
  return apiClient.get<DashboardOverview>(`${ENDPOINT}${query}`)
}
