import { apiClient } from "@/lib/api/client"
import type {
  CreateShiftAssignmentRequest,
  CreateShiftRequest,
  Shift,
  ShiftAssignment,
  UpdateShiftRequest,
  UpdateShiftAssignmentRequest,
} from "@/types/shift"

export function getShifts(): Promise<Shift[]> {
  return apiClient.get<Shift[]>("/api/shifts")
}

export function createShift(request: CreateShiftRequest): Promise<Shift> {
  return apiClient.post<Shift>("/api/shifts", request)
}

export function updateShift(code: string, request: UpdateShiftRequest): Promise<Shift> {
  return apiClient.put<Shift>(`/api/shifts/${encodeURIComponent(code)}`, request)
}

export function getShiftAssignments(from: string, to: string): Promise<ShiftAssignment[]> {
  const query = new URLSearchParams({ from, to })
  return apiClient.get<ShiftAssignment[]>(`/api/shift-assignments?${query.toString()}`)
}

export function getOwnShiftAssignments(from: string, to: string): Promise<ShiftAssignment[]> {
  const query = new URLSearchParams({ from, to })
  return apiClient.get<ShiftAssignment[]>(`/api/staff/shift-assignments?${query.toString()}`)
}

export function completeOwnShift(publicId: string): Promise<ShiftAssignment> {
  return apiClient.post<ShiftAssignment>(`/api/staff/shift-assignments/${publicId}/complete`, {})
}

export function reportOwnAbsence(publicId: string, note: string): Promise<ShiftAssignment> {
  return apiClient.post<ShiftAssignment>(`/api/staff/shift-assignments/${publicId}/absent`, { note })
}

export function createShiftAssignment(
  request: CreateShiftAssignmentRequest
): Promise<ShiftAssignment> {
  return apiClient.post<ShiftAssignment>("/api/shift-assignments", request)
}

export function updateShiftAssignment(
  publicId: string,
  request: UpdateShiftAssignmentRequest
): Promise<ShiftAssignment> {
  return apiClient.put<ShiftAssignment>(`/api/shift-assignments/${publicId}`, request)
}

export function cancelShiftAssignment(publicId: string): Promise<void> {
  return apiClient.delete<void>(`/api/shift-assignments/${publicId}`)
}
