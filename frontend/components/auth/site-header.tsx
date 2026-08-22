"use client"

import Link from "next/link"
import { useAuth } from "@/lib/auth-context"
import { Button } from "@/components/ui/button"
import { UserMenu } from "@/components/auth/user-menu"

export function SiteHeader() {
  const { isAuthenticated, isLoading } = useAuth()

  return (
    <header className="sticky top-0 z-50 w-full border-b border-[var(--border)] bg-[var(--background)]/85 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded bg-[var(--primary)] text-white font-bold text-sm">
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
          <Link href="/admin" className="text-sm font-mono font-medium text-[var(--muted-foreground)] hover:text-[var(--foreground)]">
            Quản lý
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
              <Button asChild className="bg-[var(--primary)] text-white hover:bg-[var(--primary)]/90">
                <Link href="/register">Đăng ký miễn phí</Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
