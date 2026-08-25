export type EmploymentStatus = "ACTIVE" | "ON_LEAVE" | "TERMINATED"

export interface StaffListItem {
  employeeCode: string
  fullName: string
  position: string
  department: string | null
  employmentStatus: EmploymentStatus
}
