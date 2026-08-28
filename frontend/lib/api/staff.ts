import { apiClient } from "@/lib/api/client"
import type {
  StaffHireRequest, StaffListItem, StaffManagementListItem, StaffPasswordUpdateRequest,
  StaffOwnProfile, StaffOwnProfileUpdateRequest, StaffProfile, StaffProfileUpdateRequest,
  StaffEmploymentStatusUpdateRequest,
} from "@/types/staff"

export function getStaffProfiles(active = true): Promise<StaffListItem[]> {
  const query = new URLSearchParams({ active: String(active) })
  return apiClient.get<StaffListItem[]>(`/api/staff-profiles?${query.toString()}`)
}
export function getStaffManagementProfiles(active = false): Promise<StaffManagementListItem[]> {
  const query = new URLSearchParams({ active: String(active) })
  return apiClient.get<StaffManagementListItem[]>(`/api/staff-profiles/management?${query.toString()}`)
}
export function createStaff(request: StaffHireRequest): Promise<StaffProfile> {
  return apiClient.post<StaffProfile>("/api/staff-profiles", request)
}
export function updateStaffProfile(employeeCode: string, request: StaffProfileUpdateRequest): Promise<StaffProfile> {
  return apiClient.patch<StaffProfile>(`/api/staff-profiles/${encodeURIComponent(employeeCode)}`, request)
}
export function updateStaffEmploymentStatus(employeeCode: string, request: StaffEmploymentStatusUpdateRequest): Promise<StaffProfile> {
  return apiClient.patch<StaffProfile>(`/api/staff-profiles/${encodeURIComponent(employeeCode)}/status`, request)
}
export function resetStaffPassword(employeeCode: string, request: StaffPasswordUpdateRequest): Promise<void> {
  return apiClient.patch<void>(`/api/staff-profiles/${encodeURIComponent(employeeCode)}/password`, request)
}
export function resendStaffInvitation(employeeCode: string, request: { temporaryPassword: string }): Promise<void> {
  return apiClient.post<void>(`/api/staff-profiles/${encodeURIComponent(employeeCode)}/invitation/resend`, request)
}

export function getOwnStaffProfile(): Promise<StaffOwnProfile> {
  return apiClient.get<StaffOwnProfile>("/api/staff-profiles/me")
}

export function updateOwnStaffProfile(request: StaffOwnProfileUpdateRequest): Promise<StaffOwnProfile> {
  return apiClient.patch<StaffOwnProfile>("/api/staff-profiles/me", request)
}
