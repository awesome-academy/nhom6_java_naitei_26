"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  BadgeDollarSign,
  BarChart3,
  BedDouble,
  Calendar,
  CalendarClock,
  Hotel,
  LayoutDashboard,
  LogOut,
  MessageSquareText,
  PanelLeftClose,
  PanelLeftOpen,
  ShieldCheck,
  UserCog,
  UserRound,
  Users,
  WalletCards,
} from "lucide-react"

import {
  Avatar,
  AvatarImage,
  AvatarFallback,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui"
import { isAdminUser, isBackOfficeUser, isStaffUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"
import { cn } from "@/lib/utils"

const adminNavigation = [
  { href: "/manager", label: "Dashboard", icon: LayoutDashboard },
  { href: "/manager/rooms", label: "Phòng", icon: Hotel },
  { href: "/manager/room-types", label: "Loại phòng", icon: BedDouble },
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
  const [isLogoutDialogOpen, setIsLogoutDialogOpen] = useState(false)
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
    { href: "/manager/reviews", label: "Đánh giá", icon: MessageSquareText, visible: user?.permissions.includes("review:reply") },
    { href: "/manager/shifts", label: "Lịch ca", icon: CalendarClock, visible: user?.permissions.includes("shift:read_own") },
  ].filter((item) => item.visible), [user?.permissions])
  const navigation = isAdmin ? adminNavigation : staffNavigation
  const defaultPath = isAdmin ? "/manager" : staffNavigation[0]?.href ?? "/manager/login"
  const hasAllowedStaffRoute = pathname === "/manager/profile" || pathname === "/manager/maintenance" || staffNavigation.some(
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
          "fixed left-0 top-0 z-40 flex h-screen flex-col bg-[#232323] transition-all duration-300",
          sidebarOpen ? "w-64" : "w-20",
        )}
      >
        <div
          className={cn(
            "flex h-16 items-center border-b border-white/10",
            sidebarOpen ? "justify-between px-4" : "justify-center px-3",
          )}
        >
          {sidebarOpen && (
            <Link href={defaultPath} className="flex items-center gap-2">
              <div className="flex size-8 items-center justify-center rounded-lg bg-[var(--accent)]">
                <span className="text-sm font-bold text-white">TS</span>
              </div>
              <span className="text-lg font-bold text-white">TripStay</span>
            </Link>
          )}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={sidebarOpen ? "Thu gọn thanh điều hướng" : "Mở rộng thanh điều hướng"}
            title={sidebarOpen ? "Thu gọn thanh điều hướng" : "Mở rộng thanh điều hướng"}
            onClick={() => setSidebarOpen((current) => !current)}
            className="shrink-0 text-gray-400 hover:bg-white/10 hover:text-white"
          >
            {sidebarOpen ? <PanelLeftClose data-icon="inline-start" /> : <PanelLeftOpen data-icon="inline-start" />}
          </Button>
        </div>

        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto p-3">
          {navigation.map((item) => {
            const isActive = pathname === item.href || (item.href !== "/manager" && pathname.startsWith(`${item.href}/`))
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center rounded-lg text-sm font-medium transition-colors",
                  sidebarOpen ? "w-full gap-3 px-3 py-2.5" : "mx-auto size-10 justify-center",
                  isActive ? "bg-[var(--accent)] text-white" : "text-gray-400 hover:bg-white/10 hover:text-white",
                )}
              >
                <item.icon className="size-5 shrink-0" />
                {sidebarOpen && <span>{item.label}</span>}
              </Link>
            )
          })}
          <Link
            href="/manager/profile"
            className={cn(
              "flex items-center rounded-lg text-sm font-medium transition-colors",
              sidebarOpen ? "w-full gap-3 px-3 py-2.5" : "mx-auto size-10 justify-center",
              pathname === "/manager/profile"
                ? "bg-[var(--accent)] text-white"
                : "text-gray-400 hover:bg-white/10 hover:text-white",
            )}
          >
            <UserRound className="size-5 shrink-0" />
            {sidebarOpen && <span>Hồ sơ</span>}
          </Link>
        </nav>

        <div className="flex flex-col gap-3 border-t border-white/10 p-3">
          <div className={cn("flex items-center gap-3", !sidebarOpen && "justify-center")}>
            <Avatar className="size-9">
              {user?.avatarUrl && <AvatarImage src={user.avatarUrl} alt={user.fullName ?? user.email} />}
              <AvatarFallback className="bg-[var(--accent)] text-sm text-white">{initials}</AvatarFallback>
            </Avatar>
            {sidebarOpen && (
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-white">{user?.fullName ?? "Quản lý"}</p>
                <p className="truncate text-xs text-gray-400">{user?.email ?? ""}</p>
              </div>
            )}
          </div>
          <button
            type="button"
            onClick={() => setIsLogoutDialogOpen(true)}
            aria-label="Đăng xuất"
            className={cn(
              "flex items-center rounded-lg border border-red-400/30 bg-red-500/15 text-sm font-semibold text-red-100 transition-colors hover:bg-red-500/25 hover:text-white",
              sidebarOpen ? "w-full justify-center gap-2 px-3 py-2.5" : "mx-auto size-10 justify-center",
            )}
          >
            <LogOut className="size-5 shrink-0" />
            {sidebarOpen && <span>Đăng xuất</span>}
          </button>
        </div>
      </aside>

      <div className={cn("min-w-0 flex-1 transition-all duration-300", sidebarOpen ? "ml-64" : "ml-20")}>
        <main className="min-w-0 p-6">{children}</main>
      </div>

      <Dialog open={isLogoutDialogOpen} onOpenChange={setIsLogoutDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Xác nhận đăng xuất</DialogTitle>
            <DialogDescription>
              Bạn có chắc muốn đăng xuất khỏi khu vực quản lý không?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setIsLogoutDialogOpen(false)}>
              Ở lại
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => {
                setIsLogoutDialogOpen(false)
                handleLogout()
              }}
            >
              <LogOut data-icon="inline-start" />
              Đăng xuất
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
