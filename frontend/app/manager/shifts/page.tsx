"use client"

import ShiftManagementPage from "@/components/admin/shifts/shift-management-page"
import StaffShiftSchedulePage from "@/components/staff/shifts/staff-shift-schedule-page"
import { isAdminUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"

export default function ManagerShiftsPage() {
  const { user } = useAuth()
  return isAdminUser(user) ? <ShiftManagementPage /> : <StaffShiftSchedulePage />
}
