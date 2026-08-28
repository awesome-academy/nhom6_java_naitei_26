"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/DropdownMenu";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth-context";
import { isAdminUser, isBackOfficeUser, isStaffUser } from "@/lib/admin-auth";
import { logout } from "@/lib/api/auth";
import { getStoredTokens } from "@/lib/api/auth";
import {
  Calendar,
  ChevronDown,
  LogOut,
  Settings,
  ShieldCheck,
  User,
} from "lucide-react";

function getInitials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export function UserMenu() {
  const router = useRouter();
  const { user, clearAuth } = useAuth();
  const [open, setOpen] = useState(false);

  if (!user) return null;

  const isAdmin = isAdminUser(user);
  const isStaff = isStaffUser(user);
  const isBackOffice = isBackOfficeUser(user);

  const handleLogout = async () => {
    setOpen(false);
    try {
      const { refreshToken } = getStoredTokens();
      if (refreshToken) {
        await logout({ refreshToken });
      }
    } catch {
      // ignore — clear local state regardless
    }
    clearAuth();
    toast.success("Đã đăng xuất");
    router.push("/");
    router.refresh();
  };

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="h-10 gap-3 rounded-full px-2 hover:bg-[var(--muted)]"
        >
          <Avatar className="h-8 w-8">
            {user.avatarUrl && <AvatarImage src={user.avatarUrl} alt={user.fullName || user.email} />}
            <AvatarFallback className="bg-[var(--accent)] text-white text-sm font-semibold">
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
          <Link
            href={isBackOffice ? "/manager/profile" : "/profile"}
            className="flex items-center gap-3 px-3 py-2 hover:bg-[var(--muted)] group cursor-pointer"
          >
            <User className="h-4 w-4 text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-bold transition-all" />
            <span className="text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-semibold transition-all">
              Hồ sơ
            </span>
          </Link>
        </DropdownMenuItem>
        {!isBackOffice && (
          <>
            <DropdownMenuItem asChild>
              <Link
                href="/profile/bookings"
                className="flex items-center gap-3 px-3 py-2 hover:bg-[var(--muted)] group cursor-pointer"
              >
                <Calendar className="h-4 w-4 text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-bold transition-all" />
                <span className="text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-semibold transition-all">
                  Đơn đặt phòng
                </span>
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link
                href="/profile/settings"
                className="flex items-center gap-3 px-3 py-2 hover:bg-[var(--muted)] group cursor-pointer"
              >
                <Settings className="h-4 w-4 text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-bold transition-all" />
                <span className="text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-semibold transition-all">
                  Cài đặt
                </span>
              </Link>
            </DropdownMenuItem>
          </>
        )}
        {(isAdmin || isStaff) && (
          <>
            <DropdownMenuSeparator />
            {isStaff && (
              <DropdownMenuItem asChild>
                <Link
                  href="/manager/shifts"
                  className="flex cursor-pointer items-center gap-3 px-3 py-2 hover:bg-[var(--muted)] group"
                >
                  <Calendar className="h-4 w-4 text-[var(--muted-foreground)] group-hover:text-[var(--foreground)]" />
                  <span className="text-[var(--muted-foreground)] group-hover:text-[var(--foreground)]">
                    Lịch ca của tôi
                  </span>
                </Link>
              </DropdownMenuItem>
            )}
            {isAdmin && (
              <DropdownMenuItem asChild>
                <Link
                  href="/manager"
                  className="flex items-center gap-3 px-3 py-2 hover:bg-[var(--muted)] group cursor-pointer"
                >
                  <ShieldCheck className="h-4 w-4 text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-bold transition-all" />
                  <span className="text-[var(--muted-foreground)] group-hover:text-[var(--foreground)] group-hover:font-semibold transition-all">
                    Quản trị
                  </span>
                </Link>
              </DropdownMenuItem>
            )}
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onClick={handleLogout}
          className="flex cursor-pointer items-center gap-3 px-3 py-2 hover:bg-[var(--muted)] group"
        >
          <LogOut className="h-4 w-4 text-[var(--destructive)] group-hover:font-bold transition-all" />
          <span className="text-[var(--destructive)] group-hover:font-semibold transition-all">
            Đăng xuất
          </span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
