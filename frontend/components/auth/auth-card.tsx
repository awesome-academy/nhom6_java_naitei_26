"use client"

import { useState } from "react"
import Link from "next/link"
import { cn } from "@/lib/utils"

interface AuthCardProps {
  children: React.ReactNode
  tabs?: {
    id: string
    label: string
    href?: string
    isActive?: boolean
    onClick?: () => void
  }[]
  showTabs?: boolean
}

export function AuthCard({ children, tabs, showTabs = true }: AuthCardProps) {
  return (
    <div className="w-full rounded-xl border border-[var(--border)] bg-[var(--card)] shadow-[0_18px_44px_rgba(40,70,160,0.08)] overflow-hidden">
      {showTabs && tabs && tabs.length > 0 && (
        <div className="grid grid-cols-2 border-b border-[var(--border)] bg-[var(--muted)]/30">
          {tabs.map((tab) => (
            <Link
              key={tab.id}
              href={tab.href || "#"}
              onClick={tab.onClick}
              className={cn(
                "flex h-13 items-center justify-center text-base font-semibold transition-all border-b-2",
                tab.isActive
                  ? "bg-[var(--card)] text-[var(--foreground)] border-b-[var(--primary)]"
                  : "text-[var(--muted-foreground)] border-b-transparent hover:text-[var(--foreground)]"
              )}
              style={{ minHeight: "52px" }}
            >
              {tab.label}
            </Link>
          ))}
        </div>
      )}
      <div className="p-7">{children}</div>
    </div>
  )
}

interface DividerProps {
  text: string
}

export function FormDivider({ text }: DividerProps) {
  return (
    <div className="relative my-5">
      <div className="absolute inset-0 flex items-center">
        <div className="w-full border-t border-[var(--border)]" />
      </div>
      <div className="relative flex justify-center">
        <span className="bg-[var(--card)] px-3 text-sm text-[var(--muted-foreground)]">
          {text}
        </span>
      </div>
    </div>
  )
}

interface SocialButtonProps {
  provider: "google" | "apple" | "facebook"
  onClick: () => void
}

export function SocialButton({ provider, onClick }: SocialButtonProps) {
  const icons = {
    google: (
      <svg className="h-6 w-6" viewBox="0 0 24 24">
        <path d="M22.6 12.25c0-.74-.07-1.45-.19-2.12H12v4.02h5.95a5.08 5.08 0 0 1-2.2 3.33v2.76h3.56c2.08-1.92 3.29-4.75 3.29-7.99Z" fill="#4285F4" />
        <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.56-2.76c-.98.66-2.23 1.05-3.72 1.05-2.86 0-5.29-1.93-6.16-4.53H2.18v2.85A11 11 0 0 0 12 23Z" fill="#34A853" />
        <path d="M5.84 14.1A6.6 6.6 0 0 1 5.5 12c0-.73.12-1.43.34-2.1V7.05H2.18A11 11 0 0 0 1 12c0 1.77.42 3.44 1.18 4.95l3.66-2.85Z" fill="#FBBC05" />
        <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.05L5.84 9.9C6.71 7.31 9.14 5.38 12 5.38Z" fill="#EA4335" />
      </svg>
    ),
    apple: (
      <svg className="h-6 w-6 text-[var(--foreground)]" viewBox="0 0 24 24">
        <path
          fill="currentColor"
          d="M16.36 12.91c-.02-2.19 1.79-3.24 1.87-3.29-1.02-1.49-2.6-1.69-3.16-1.72-1.34-.14-2.61.79-3.29.79-.68 0-1.73-.77-2.84-.75-1.46.02-2.8.85-3.55 2.16-1.52 2.64-.39 6.55 1.09 8.69.72 1.04 1.58 2.22 2.71 2.17 1.09-.04 1.5-.7 2.81-.7 1.31 0 1.68.7 2.83.68 1.17-.02 1.91-1.06 2.63-2.1.83-1.21 1.17-2.38 1.19-2.44-.03-.01-2.27-.87-2.29-3.49Zm-2.17-6.42c.6-.73 1.01-1.75.9-2.76-.87.04-1.93.58-2.55 1.31-.56.65-1.05 1.69-.92 2.69.98.08 1.96-.49 2.57-1.24Z"
        />
      </svg>
    ),
    facebook: (
      <svg className="h-6 w-6 text-[#1877F2]" viewBox="0 0 24 24" fill="currentColor">
        <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
      </svg>
    ),
  }

  const labels = { google: "Google", apple: "Apple", facebook: "Facebook" }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`Đăng nhập với ${labels[provider]}`}
      className="flex h-12 w-full items-center justify-center rounded-md border border-[var(--border)] bg-[var(--card)] text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]/30 hover:border-[var(--foreground)]/40"
    >
      {icons[provider]}
    </button>
  )
}

interface FieldProps {
  label: string
  htmlFor: string
  required?: boolean
  optional?: boolean
  error?: string
  isInvalid?: boolean
  children: React.ReactNode
}

export function FormField({ label, htmlFor, required, optional, error, isInvalid, children }: FieldProps) {
  return (
    <div className="space-y-1.5">
      <label
        htmlFor={htmlFor}
        className={cn(
          "block text-sm font-semibold",
          isInvalid ? "text-[var(--destructive)]" : "text-[var(--foreground)]"
        )}
      >
        {label}
        {required && <span className="text-[var(--destructive)] ml-1">*</span>}
        {optional && <span className="text-[var(--muted-foreground)] font-normal ml-1">(không bắt buộc)</span>}
      </label>
      {children}
      {error && (
        <p className="text-xs text-[var(--destructive)] mt-1">{error}</p>
      )}
    </div>
  )
}