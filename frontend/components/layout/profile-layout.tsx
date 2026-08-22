"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/lib/auth-context"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui"
import { Button } from "@/components/ui/button"
import { UserMenu } from "@/components/auth/user-menu"
import { toast } from "sonner"
import { cn } from "@/lib/utils"
import { logout, getStoredTokens } from "@/lib/api/auth"
import {
  User,
  Calendar,
  CreditCard,
  Settings,
  LogOut,
  ChevronRight,
  Crown,
} from "lucide-react"

const profileNav = [
  { href: "/profile", label: "Thông tin cá nhân", icon: User },
  { href: "/profile/bookings", label: "Đơn đặt phòng", icon: Calendar },
  { href: "/profile/settings", label: "Cài đặt", icon: Settings },
]

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase()
}

interface ProfileLayoutProps {
  children: React.ReactNode
}

export function ProfileLayout({ children }: ProfileLayoutProps) {
  const pathname = usePathname()
  const router = useRouter()
  const { user, clearAuth, isAuthenticated, isLoading } = useAuth()

  const handleLogout = async () => {
    try {
      const { refreshToken } = getStoredTokens()
      if (refreshToken) {
        await logout({ refreshToken })
      }
    } catch {
      // ignore
    }
    clearAuth()
    toast.success("Đã đăng xuất")
    router.push("/")
    router.refresh()
  }

  return (
    <div className="min-h-screen bg-[var(--background)]">
      {/* Header - matching marketing landing style */}
      <header className="sticky top-0 z-50 w-full border-b border-[var(--border)] bg-[var(--background)]/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
          <Link href="/" className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-[var(--accent)] text-white font-bold text-sm">
              T
            </div>
            <span className="text-base font-mono font-bold tracking-wider uppercase text-[var(--foreground)]">
              TripStay
            </span>
          </Link>
          <nav className="hidden gap-8 md:flex">
            <Link href="#rooms" className="text-sm font-mono font-medium text-[var(--muted-foreground)] hover:text-[var(--foreground)]">
              Khách sạn
            </Link>
            <Link href="#how-it-works" className="text-sm font-mono font-medium text-[var(--muted-foreground)] hover:text-[var(--foreground)]">
              Cách hoạt động
            </Link>
            <Link href="#pricing" className="text-sm font-mono font-medium text-[var(--muted-foreground)] hover:text-[var(--foreground)]">
              Bảng giá
            </Link>
          </nav>
          <div className="flex items-center gap-3">
            {isLoading ? (
              <div className="h-10 w-24" />
            ) : isAuthenticated ? (
              <UserMenu />
            ) : (
              <>
                <Button variant="ghost" asChild className="hidden md:inline-flex">
                  <Link href="/login">Đăng nhập</Link>
                </Button>
                <Button asChild className="bg-[var(--accent)] text-white hover:bg-[var(--accent)]/90">
                  <Link href="/register">Đăng ký miễn phí</Link>
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-7xl px-6 py-8">
        <div className="grid gap-8 lg:grid-cols-[280px_1fr]">
          {/* Sidebar */}
          <aside className="space-y-6">
            {/* User Card */}
            <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
              <div className="flex flex-col items-center text-center">
                <Avatar className="h-20 w-20">
                  <AvatarFallback className="bg-[var(--accent)] text-white text-2xl font-medium">
                    {getInitials(user?.fullName || user?.email || "U")}
                  </AvatarFallback>
                </Avatar>
                <h2 className="mt-4 text-lg font-semibold text-[var(--foreground)]">
                  {user?.fullName || "Khách hàng"}
                </h2>
                <p className="text-sm text-[var(--muted-foreground)]">{user?.email}</p>

                {/* Loyalty Card */}
                <div className="mt-4 w-full rounded-lg bg-[var(--muted)] p-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Crown className="h-4 w-4 text-yellow-500" />
                      <span className="text-xs text-[var(--muted-foreground)]">Điểm tích lũy</span>
                    </div>
                    <Badge variant="default" className="bg-[var(--muted-foreground)] text-white">
                      0
                    </Badge>
                  </div>
                  <p className="mt-2 text-2xl font-bold text-[var(--foreground)]">
                    0
                  </p>
                </div>
              </div>
            </div>

            {/* Navigation */}
            <nav className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-2">
              {profileNav.map((item) => {
                const isActive = pathname === item.href
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "flex items-center gap-3 rounded-lg px-4 py-3 text-sm font-medium transition-colors",
                      isActive
                        ? "bg-[var(--accent)] text-white"
                        : "text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                    )}
                  >
                    <item.icon className="h-5 w-5" />
                    <span className="flex-1">{item.label}</span>
                    {isActive && <ChevronRight className="h-4 w-4" />}
                  </Link>
                )
              })}
              <div className="my-2 border-t border-[var(--border)]" />
              <button
                onClick={handleLogout}
                className="flex w-full cursor-pointer items-center gap-3 rounded-lg px-4 py-3 text-sm font-medium text-[var(--destructive)] transition-colors hover:bg-[var(--destructive)]/10"
              >
                <LogOut className="h-5 w-5" />
                <span>Đăng xuất</span>
              </button>
            </nav>
          </aside>

          {/* Main Content */}
          <main className="min-w-0">{children}</main>
        </div>
      </div>
    </div>
  )
}
