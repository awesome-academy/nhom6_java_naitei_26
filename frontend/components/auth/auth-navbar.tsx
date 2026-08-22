"use client"

import Link from "next/link"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui"
import { ChevronDown } from "lucide-react"

export function AuthNavbar() {
  return (
    <header className="sticky top-0 z-10 w-full border-b border-[var(--border)] bg-[var(--card)]/85 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-[1120px] items-center justify-between px-8">
        <Link href="/" className="text-lg font-semibold tracking-tight text-[var(--foreground)]">
          TripStay
        </Link>

        <nav className="hidden gap-6 md:flex">
          <Link
            href="/hotels"
            className="text-sm text-[var(--muted-foreground)] transition-colors hover:text-[var(--foreground)]"
          >
            Khách sạn
          </Link>
          <Link
            href="/bookings"
            className="text-sm text-[var(--muted-foreground)] transition-colors hover:text-[var(--foreground)]"
          >
            Đơn đặt phòng
          </Link>
          <Link
            href="/support"
            className="text-sm text-[var(--muted-foreground)] transition-colors hover:text-[var(--foreground)]"
          >
            H� trợ
          </Link>
        </nav>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm" className="font-medium">
              VI · VND
              <ChevronDown className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>Tiếng Việt · VND</DropdownMenuItem>
            <DropdownMenuItem>English · USD</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}