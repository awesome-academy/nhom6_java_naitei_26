export type AssignmentStatus = "SCHEDULED" | "COMPLETED" | "ABSENT" | "CANCELLED"

export interface Shift {
  code: string
  name: string
  startTime: string
  endTime: string
  crossesMidnight: boolean
  isActive: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface ShiftAssignment {
  publicId: string
  employeeCode: string
  staffName: string
  shiftCode: string
  shiftName: string
  workDate: string
  shiftStartAt: string
  shiftEndAt: string
  status: AssignmentStatus
  note: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface CreateShiftRequest {
  code: string
  name: string
  startTime: string
  endTime: string
  crossesMidnight: boolean
  isActive: boolean
}

export interface UpdateShiftRequest {
  name: string
  startTime: string
  endTime: string
  crossesMidnight: boolean
  isActive: boolean
}

export interface CreateShiftAssignmentRequest {
  employeeCode: string
  shiftCode: string
  workDate: string
  note: string | null
}

export interface UpdateShiftAssignmentRequest extends CreateShiftAssignmentRequest {
  status: AssignmentStatus
}
