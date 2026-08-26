"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui"
import {
  LayoutDashboard,
  Hotel,
  BedDouble,
  Wrench,
  BadgeDollarSign,
  ShieldCheck,
  Calendar,
  CalendarClock,
  Users,
  UserCog,
  BarChart3,
  WalletCards,
  Settings,
  Bell,
  Search,
  LogOut,
  Menu,
  X,
  ChevronDown,
} from "lucide-react"
import { isBackOfficeUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"

const adminNav = [
  { href: "/admin", label: "Dashboard", icon: LayoutDashboard },
  { href: "/admin/rooms", label: "Phòng", icon: Hotel },
  { href: "/admin/room-types", label: "Loại phòng", icon: BedDouble },
  { href: "/admin/maintenance", label: "Lịch bảo trì", icon: Wrench },
  { href: "/admin/pricing", label: "Quản lý giá", icon: BadgeDollarSign },
  { href: "/admin/cancellation-policies", label: "Chính sách hủy", icon: ShieldCheck },
  { href: "/admin/bookings", label: "Đặt phòng", icon: Calendar },
  { href: "/admin/shifts", label: "Ca trực", icon: CalendarClock },
  { href: "/admin/payments", label: "Thanh toán", icon: WalletCards },
  { href: "/admin/guests", label: "Khách hàng", icon: Users },
  { href: "/admin/staff", label: "Nhân viên", icon: UserCog },
  { href: "/admin/reports", label: "Báo cáo", icon: BarChart3 },
  { href: "/admin/settings", label: "Cài đặt", icon: Settings },
]

interface AdminLayoutProps {
  children: React.ReactNode
}

export function AdminLayout({ children }: AdminLayoutProps) {
  const router = useRouter()
  const pathname = usePathname()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const { user, isAuthenticated, isLoading, clearAuth } = useAuth()
  const isAdminLoginPage = pathname === "/admin/login"
  const hasAdminAccess = isBackOfficeUser(user)
  const adminInitials = user?.fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "AD"

  useEffect(() => {
    if (isAdminLoginPage || isLoading) return

    const redirect = encodeURIComponent(pathname || "/admin")
    if (!isAuthenticated) {
      router.replace(`/admin/login?redirect=${redirect}`)
      return
    }

    if (!hasAdminAccess) {
      router.replace(`/admin/login?redirect=${redirect}&reason=forbidden`)
    }
  }, [hasAdminAccess, isAdminLoginPage, isAuthenticated, isLoading, pathname, router])

  function handleLogout() {
    clearAuth()
    router.replace("/admin/login")
  }

  if (isAdminLoginPage) {
    return <>{children}</>
  }

  if (isLoading || !isAuthenticated || !hasAdminAccess) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="text-sm text-muted-foreground">Đang kiểm tra quyền quản trị...</div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-[var(--background)]">
      {/* Sidebar */}
      <aside
        className={cn(
          "fixed left-0 top-0 z-40 h-screen bg-[#232323] transition-all duration-300",
          sidebarOpen ? "w-64" : "w-20"
        )}
      >
        <div className="flex h-16 items-center justify-between border-b border-white/10 px-4">
          <Link href="/admin" className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--accent)]">
              <span className="text-sm font-bold text-white">TS</span>
            </div>
            {sidebarOpen && (
              <span className="text-lg font-bold text-white">TripStay</span>
            )}
          </Link>
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="rounded-md p-1.5 text-gray-400 hover:bg-white/10 hover:text-white"
          >
            {sidebarOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>

        <nav className="space-y-1 p-3">
          {adminNav.map((item) => {
            const isActive = pathname === item.href
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-[var(--accent)] text-white"
                    : "text-gray-400 hover:bg-white/10 hover:text-white"
                )}
              >
                <item.icon className="h-5 w-5 shrink-0" />
                {sidebarOpen && <span>{item.label}</span>}
              </Link>
            )
          })}
        </nav>

        {/* User Section */}
        <div className="absolute bottom-0 left-0 right-0 border-t border-white/10 p-3">
          <div className={cn("flex items-center gap-3", !sidebarOpen && "justify-center")}>
            <Avatar className="h-9 w-9">
              <AvatarImage src="/avatars/admin.jpg" />
              <AvatarFallback className="bg-[var(--accent)] text-white text-sm">{adminInitials}</AvatarFallback>
            </Avatar>
            {sidebarOpen && (
              <div className="flex-1 overflow-hidden">
                <p className="truncate text-sm font-medium text-white">{user?.fullName ?? "Admin"}</p>
                <p className="truncate text-xs text-gray-400">{user?.email ?? ""}</p>
              </div>
            )}
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <div className={cn("flex-1 transition-all duration-300", sidebarOpen ? "ml-64" : "ml-20")}>
        {/* Header */}
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-[var(--border)] bg-[var(--card)] px-6">
          <div className="flex items-center gap-4">
            <button
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="rounded-md p-1.5 text-[var(--muted-foreground)] hover:bg-[var(--muted)] lg:hidden"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="relative hidden md:block">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
              <input
                type="search"
                placeholder="Tìm kiếm..."
                className="h-9 w-64 rounded-md border border-[var(--border)] bg-[var(--background)] pl-9 pr-4 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--ring)]"
              />
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" className="relative">
              <Bell className="h-5 w-5" />
              <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-[var(--destructive)]" />
            </Button>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="flex items-center gap-2">
                  <Avatar className="h-8 w-8">
                    <AvatarImage src="/avatars/admin.jpg" />
                    <AvatarFallback className="bg-[var(--accent)] text-white text-xs">{adminInitials}</AvatarFallback>
                  </Avatar>
                  <span className="hidden md:inline text-sm font-medium">{user?.fullName ?? "Admin"}</span>
                  <ChevronDown className="h-4 w-4 text-[var(--muted-foreground)]" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>Tài khoản của tôi</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link href="/profile">Hồ sơ</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link href="/admin/settings">Cài đặt</Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="text-[var(--destructive)]" onClick={handleLogout}>
                  <LogOut data-icon="inline-start" />
                  Đăng xuất
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>

        {/* Page Content */}
        <main className="p-6">{children}</main>
      </div>
    </div>
  )
}
