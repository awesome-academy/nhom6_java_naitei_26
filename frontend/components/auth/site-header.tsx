"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useAuth } from "@/lib/auth-context"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { UserMenu } from "@/components/auth/user-menu"

const mainNav = [
  { href: "/", label: "Trang chủ" },
  { href: "/booking", label: "Đặt phòng" },
]

export function SiteHeader() {
  const { isAuthenticated, isLoading } = useAuth()
  const pathname = usePathname()

  return (
    <header className="sticky top-0 z-50 w-full border-b border-[var(--border)] bg-[var(--background)]/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-2">
          <div className="flex size-8 items-center justify-center rounded bg-[var(--accent)] text-sm font-bold text-white">
            T
          </div>
          <span className="text-base font-mono font-bold tracking-wider uppercase text-[var(--foreground)]">
            TripStay
          </span>
        </Link>
        <nav className="hidden gap-8 md:flex">
          {mainNav.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "text-sm font-mono font-medium hover:text-[var(--foreground)]",
                pathname === item.href
                  ? "text-[var(--foreground)]"
                  : "text-[var(--muted-foreground)]",
              )}
            >
              {item.label}
            </Link>
          ))}
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
  )
}
