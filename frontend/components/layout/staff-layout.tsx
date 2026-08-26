"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { CalendarClock, LogOut, Menu, X } from "lucide-react"
import { toast } from "sonner"

import { Avatar, AvatarFallback, Button } from "@/components/ui"
import { getStoredTokens, logout } from "@/lib/api/auth"
import { useAuth } from "@/lib/auth-context"
import { isStaffUser } from "@/lib/admin-auth"
import { cn } from "@/lib/utils"

export function StaffLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const { user, isAuthenticated, isLoading, clearAuth } = useAuth()
  const currentUser = user
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const isStaff = isStaffUser(user)

  useEffect(() => {
    if (isLoading) return
    if (!isAuthenticated) {
      router.replace(`/admin/login?redirect=${encodeURIComponent(pathname || "/staff/shifts")}`)
      return
    }
    if (!isStaff) {
      router.replace("/admin")
    }
  }, [isAuthenticated, isLoading, isStaff, pathname, router])

  async function handleLogout() {
    try {
      const { refreshToken } = getStoredTokens()
      if (refreshToken) await logout({ refreshToken })
    } catch {
      // The local session is cleared even when the server logout request fails.
    }
    clearAuth()
    toast.success("Đã đăng xuất")
    router.replace("/")
  }

  if (isLoading || !isAuthenticated || !isStaff) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-sm text-muted-foreground">Đang kiểm tra tài khoản Staff...</p>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-background">
      <aside
        className={cn(
          "fixed left-0 top-0 z-40 h-screen border-r border-border bg-card transition-all duration-300",
          sidebarOpen ? "w-64" : "w-20"
        )}
      >
        <div className="flex h-16 items-center justify-between border-b border-border px-4">
          <Link href="/staff/shifts" className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary">
              <span className="text-sm font-bold text-primary-foreground">TS</span>
            </div>
            {sidebarOpen && <span className="text-lg font-bold text-white">TripStay</span>}
          </Link>
          <button
            type="button"
            aria-label={sidebarOpen ? "Thu gọn thanh điều hướng" : "Mở thanh điều hướng"}
            onClick={() => setSidebarOpen((current) => !current)}
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            {sidebarOpen ? <X /> : <Menu />}
          </button>
        </div>

        <nav className="flex flex-col gap-1 p-3">
          <Link
            href="/staff/shifts"
            className={cn(
              "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
              pathname.startsWith("/staff/shifts")
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-muted hover:text-foreground"
            )}
          >
            <CalendarClock className="size-5 shrink-0" />
            {sidebarOpen && <span>Lịch ca</span>}
          </Link>
        </nav>

        <div className="absolute bottom-0 left-0 right-0 border-t border-border p-3">
          <div className={cn("flex items-center gap-3", !sidebarOpen && "justify-center")}>
            <Avatar className="size-9">
              <AvatarFallback className="bg-primary text-sm text-primary-foreground">
                {(currentUser?.fullName ?? "")
                  .split(/\s+/)
                  .filter(Boolean)
                  .slice(-2)
                  .map((part) => part[0])
                  .join("")
                  .toUpperCase() || "ST"}
              </AvatarFallback>
            </Avatar>
            {sidebarOpen && (
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-foreground">{currentUser?.fullName}</p>
                <p className="truncate text-xs text-muted-foreground">{currentUser?.email}</p>
              </div>
            )}
          </div>
          {sidebarOpen && (
            <Button
              type="button"
              variant="ghost"
              className="mt-3 w-full justify-start text-muted-foreground hover:bg-muted hover:text-foreground"
              onClick={handleLogout}
            >
              <LogOut data-icon="inline-start" />
              Đăng xuất
            </Button>
          )}
        </div>
      </aside>

      <main className={cn("min-w-0 flex-1 transition-all duration-300", sidebarOpen ? "ml-64" : "ml-20")}>
        <header className="sticky top-0 z-30 flex h-16 items-center border-b border-border bg-card px-6">
          <button
            type="button"
            aria-label="Mở thanh điều hướng"
            onClick={() => setSidebarOpen(true)}
            className="mr-4 rounded-md p-1.5 text-muted-foreground hover:bg-muted lg:hidden"
          >
            <Menu />
          </button>
          <div>
            <p className="text-xs uppercase tracking-wider text-muted-foreground">Khu vực Staff</p>
            <h1 className="font-semibold text-foreground">Lịch ca của tôi</h1>
          </div>
        </header>
        <div className="p-6">{children}</div>
      </main>
    </div>
  )
}
