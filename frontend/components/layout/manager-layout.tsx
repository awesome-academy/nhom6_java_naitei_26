"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  BadgeDollarSign,
  BarChart3,
  BedDouble,
  Bell,
  Calendar,
  CalendarClock,
  ChevronDown,
  Hotel,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquareText,
  Search,
  ShieldCheck,
  UserCog,
  Users,
  WalletCards,
  Wrench,
  X,
} from "lucide-react"

import { Avatar, AvatarFallback, Button } from "@/components/ui"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui"
import { isAdminUser, isBackOfficeUser, isStaffUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"
import { cn } from "@/lib/utils"

const adminNavigation = [
  { href: "/manager", label: "Dashboard", icon: LayoutDashboard },
  { href: "/manager/rooms", label: "Phòng", icon: Hotel },
  { href: "/manager/room-types", label: "Loại phòng", icon: BedDouble },
  { href: "/manager/maintenance", label: "Lịch bảo trì", icon: Wrench },
  { href: "/manager/pricing", label: "Quản lý giá", icon: BadgeDollarSign },
  { href: "/manager/cancellation-policies", label: "Chính sách hủy", icon: ShieldCheck },
  { href: "/manager/bookings", label: "Đặt phòng", icon: Calendar },
  { href: "/manager/shifts", label: "Ca trực", icon: CalendarClock },
  { href: "/manager/payments", label: "Thanh toán", icon: WalletCards },
  { href: "/manager/reviews", label: "Đánh giá", icon: MessageSquareText },
  { href: "/manager/guests", label: "Khách hàng", icon: Users },
  { href: "/manager/staff", label: "Nhân viên", icon: UserCog },
  { href: "/manager/reports", label: "Báo cáo", icon: BarChart3 },
]

interface ManagerLayoutProps {
  children: React.ReactNode
}

export function ManagerLayout({ children }: ManagerLayoutProps) {
  const router = useRouter()
  const pathname = usePathname()
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const { user, isAuthenticated, isLoading, clearAuth } = useAuth()
  const isManagerLoginPage = pathname === "/manager/login"
  const isAdmin = isAdminUser(user)
  const isStaff = isStaffUser(user)
  const hasManagerAccess = isBackOfficeUser(user)
  const staffNavigation = useMemo(() => [
    {
      href: "/manager/bookings",
      label: "Đặt phòng",
      icon: Calendar,
      visible: user?.permissions.includes("booking:check_in") || user?.permissions.includes("booking:check_out"),
    },
    { href: "/manager/payments", label: "Thanh toán", icon: WalletCards, visible: user?.permissions.includes("payment:manage") },
    { href: "/manager/rooms", label: "Phòng", icon: Hotel, visible: user?.permissions.includes("room:read") },
    { href: "/manager/room-types", label: "Loại phòng", icon: BedDouble, visible: user?.permissions.includes("room:read") },
    { href: "/manager/maintenance", label: "Lịch bảo trì", icon: Wrench, visible: user?.permissions.includes("maintenance:manage") },
    { href: "/manager/reviews", label: "Đánh giá", icon: MessageSquareText, visible: user?.permissions.includes("review:reply") },
    { href: "/manager/shifts", label: "Lịch ca", icon: CalendarClock, visible: user?.permissions.includes("shift:read_own") },
  ].filter((item) => item.visible), [user?.permissions])
  const navigation = isAdmin ? adminNavigation : staffNavigation
  const defaultPath = isAdmin ? "/manager" : staffNavigation[0]?.href ?? "/manager/login"
  const hasAllowedStaffRoute = staffNavigation.some(
    (item) => pathname === item.href || pathname.startsWith(`${item.href}/`),
  )
  const initials = user?.fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "MG"

  useEffect(() => {
    if (isManagerLoginPage || isLoading) return

    const redirect = encodeURIComponent(pathname || "/manager")
    if (!isAuthenticated) {
      router.replace(`/manager/login?redirect=${redirect}`)
      return
    }
    if (!hasManagerAccess) {
      router.replace(`/manager/login?redirect=${redirect}&reason=forbidden`)
      return
    }
    if (isStaff) {
      if (!hasAllowedStaffRoute) router.replace(defaultPath)
    }
  }, [defaultPath, hasAllowedStaffRoute, hasManagerAccess, isAuthenticated, isLoading, isManagerLoginPage, isStaff, pathname, router])

  function handleLogout() {
    clearAuth()
    router.replace("/manager/login")
  }

  if (isManagerLoginPage) return <>{children}</>

  if (isLoading || !isAuthenticated || !hasManagerAccess || (isStaff && (!navigation.length || !hasAllowedStaffRoute))) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="text-sm text-muted-foreground">Đang kiểm tra quyền truy cập...</div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen bg-[var(--background)]">
      <aside
        className={cn(
          "fixed left-0 top-0 z-40 h-screen bg-[#232323] transition-all duration-300",
          sidebarOpen ? "w-64" : "w-20",
        )}
      >
        <div className="flex h-16 items-center justify-between border-b border-white/10 px-4">
          <Link href={defaultPath} className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-[var(--accent)]">
              <span className="text-sm font-bold text-white">TS</span>
            </div>
            {sidebarOpen && <span className="text-lg font-bold text-white">TripStay</span>}
          </Link>
          <button
            type="button"
            aria-label={sidebarOpen ? "Thu gọn thanh điều hướng" : "Mở thanh điều hướng"}
            onClick={() => setSidebarOpen((current) => !current)}
            className="rounded-md p-1.5 text-gray-400 hover:bg-white/10 hover:text-white"
          >
            {sidebarOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>

        <nav className="flex flex-col gap-1 p-3">
          {navigation.map((item) => {
            const isActive = pathname === item.href || (item.href !== "/manager" && pathname.startsWith(`${item.href}/`))
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  isActive ? "bg-[var(--accent)] text-white" : "text-gray-400 hover:bg-white/10 hover:text-white",
                )}
              >
                <item.icon className="size-5 shrink-0" />
                {sidebarOpen && <span>{item.label}</span>}
              </Link>
            )
          })}
        </nav>

        <div className="absolute bottom-0 left-0 right-0 border-t border-white/10 p-3">
          <div className={cn("flex items-center gap-3", !sidebarOpen && "justify-center")}>
            <Avatar className="size-9">
              <AvatarFallback className="bg-[var(--accent)] text-sm text-white">{initials}</AvatarFallback>
            </Avatar>
            {sidebarOpen && (
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-white">{user?.fullName ?? "Quản lý"}</p>
                <p className="truncate text-xs text-gray-400">{user?.email ?? ""}</p>
              </div>
            )}
          </div>
        </div>
      </aside>

      <div className={cn("flex-1 transition-all duration-300", sidebarOpen ? "ml-64" : "ml-20")}>
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-[var(--border)] bg-[var(--card)] px-6">
          <div className="flex items-center gap-4">
            <button
              type="button"
              aria-label="Mở thanh điều hướng"
              onClick={() => setSidebarOpen(true)}
              className="rounded-md p-1.5 text-[var(--muted-foreground)] hover:bg-[var(--muted)] lg:hidden"
            >
              <Menu className="size-5" />
            </button>
            <div className="relative hidden md:block">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
              <input
                type="search"
                placeholder="Tìm kiếm..."
                className="h-9 w-64 rounded-md border border-[var(--border)] bg-[var(--background)] pl-9 pr-4 text-sm focus:outline-none focus:ring-2 focus:ring-[var(--ring)]"
              />
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" className="relative">
              <Bell />
              <span className="absolute right-1 top-1 size-2 rounded-full bg-[var(--destructive)]" />
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" className="flex items-center gap-2">
                  <Avatar className="size-8">
                    <AvatarFallback className="bg-[var(--accent)] text-xs text-white">{initials}</AvatarFallback>
                  </Avatar>
                  <span className="hidden text-sm font-medium md:inline">{user?.fullName ?? "Quản lý"}</span>
                  <ChevronDown className="size-4 text-[var(--muted-foreground)]" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>Tài khoản của tôi</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link href="/profile">Hồ sơ</Link>
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
        <main className="p-6">{children}</main>
      </div>
    </div>
  )
}
