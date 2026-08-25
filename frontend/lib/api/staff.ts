import { apiClient } from "@/lib/api/client"
import type { StaffListItem } from "@/types/staff"

export function getStaffProfiles(active = true): Promise<StaffListItem[]> {
  const query = new URLSearchParams({ active: String(active) })
  return apiClient.get<StaffListItem[]>(`/api/staff-profiles?${query.toString()}`)
}
