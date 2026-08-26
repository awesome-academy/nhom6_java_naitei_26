import type { UserStatus } from "@/types/auth"

export type EmploymentStatus = "ACTIVE" | "ON_LEAVE" | "TERMINATED"

export interface StaffListItem {
  employeeCode: string
  fullName: string
  position: string | null
  department: string | null
  employmentStatus: EmploymentStatus
}
export interface StaffManagementListItem extends StaffListItem {
  email: string
  accountStatus: UserStatus
  emailVerifiedAt: string | null
  hiredAt: string
  terminatedAt: string | null
}
export interface StaffProfile {
  employeeCode: string
  userPublicId: string
  fullName: string
  email: string
  position: string | null
  department: string | null
  hiredAt: string
  terminatedAt: string | null
  employmentStatus: EmploymentStatus
  accountStatus: UserStatus
  emailVerifiedAt: string | null
  baseSalary: number | string | null
  createdAt: string
  updatedAt: string
}
export interface StaffHireRequest {
  email: string
  fullName: string
  temporaryPassword: string
  phone?: string | null
  position?: string | null
  department?: string | null
  hiredAt?: string | null
  baseSalary?: number | null
}
export interface StaffPasswordUpdateRequest { newPassword: string }
export interface StaffProfileUpdateRequest {
  position?: string | null
  department?: string | null
  baseSalary?: number | null
}
export interface StaffEmploymentStatusUpdateRequest { employmentStatus: EmploymentStatus }
