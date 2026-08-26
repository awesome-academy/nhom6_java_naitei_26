import type { UserSummary } from "@/types/auth"

export function isAdminUser(user: UserSummary | null): boolean {
  return user?.roles.includes("ADMIN") ?? false
}

export function isBackOfficeUser(user: UserSummary | null): boolean {
  return user?.roles.some((role) => role === "ADMIN" || role === "STAFF") ?? false
}

export function isStaffUser(user: UserSummary | null): boolean {
  return user?.roles.includes("STAFF") ?? false
}
