"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import {
  Avatar,
  AvatarFallback,
} from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/lib/auth-context"
import { logout } from "@/lib/api/auth"
import { getStoredTokens } from "@/lib/api/auth"
import {
  Calendar,
  CreditCard,
  Heart,
  LogOut,
  Settings,
  ShieldCheck,
  User,
  ChevronDown,
} from "lucide-react"

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase()
}

export function UserMenu() {
  const router = useRouter()
  const { user, clearAuth } = useAuth()
  const [open, setOpen] = useState(false)

  if (!user) return null

  const isStaff =
    user.roles?.includes("ADMIN") || user.roles?.includes("STAFF")

  const handleLogout = async () => {
    setOpen(false)
    try {
      const { refreshToken } = getStoredTokens()
      if (refreshToken) {
        await logout({ refreshToken })
      }
    } catch {
      // ignore — clear local state regardless
    }
    clearAuth()
    toast.success("Đã đăng xuất")
    router.push("/")
    router.refresh()
  }

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="h-10 gap-3 rounded-full px-2 hover:bg-[var(--muted)]"
        >
          <Avatar className="h-8 w-8">
            <AvatarFallback className="bg-[var(--primary)] text-[var(--primary-foreground)] text-sm font-semibold">
              {getInitials(user.fullName || user.email)}
            </AvatarFallback>
          </Avatar>
          <span className="hidden text-sm font-medium text-[var(--foreground)] sm:inline-block max-w-[140px] truncate">
            {user.fullName || user.email}
          </span>
          <ChevronDown className="hidden h-4 w-4 text-[var(--muted-foreground)] sm:inline-block" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64 p-1.5">
        <DropdownMenuLabel className="flex flex-col gap-1 px-3 py-2">
          <span className="text-sm font-semibold text-[var(--foreground)]">
            {user.fullName || "Khách hàng"}
          </span>
          <span className="text-xs font-normal text-[var(--muted-foreground)] truncate">
            {user.email}
          </span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/profile" className="flex items-center gap-3 px-3 py-2">
            <User className="h-4 w-4 text-[var(--muted-foreground)]" />
            <span>Hồ sơ</span>
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/profile/bookings" className="flex items-center gap-3 px-3 py-2">
            <Calendar className="h-4 w-4 text-[var(--muted-foreground)]" />
            <span>Đơn đặt phòng</span>
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/profile/settings" className="flex items-center gap-3 px-3 py-2">
            <Settings className="h-4 w-4 text-[var(--muted-foreground)]" />
            <span>Cài đặt</span>
          </Link>
        </DropdownMenuItem>
        {isStaff && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link href="/admin" className="flex items-center gap-3 px-3 py-2">
                <ShieldCheck className="h-4 w-4 text-[var(--muted-foreground)]" />
                <span>Quản trị</span>
              </Link>
            </DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onClick={handleLogout}
          className="flex cursor-pointer items-center gap-3 px-3 py-2 text-[var(--destructive)] focus:text-[var(--destructive)]"
        >
          <LogOut className="h-4 w-4" />
          <span>Đăng xuất</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
