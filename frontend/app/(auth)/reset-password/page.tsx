import { Suspense } from "react"
import Link from "next/link"
import { AuthLayoutNew } from "@/components/auth/auth-layout-new"
import { ResetPasswordFormNew } from "@/components/auth/reset-password-form-new"

function ResetPasswordContent() {
  return (
    <div className="w-full max-w-md space-y-8">
      {/* Mobile Logo */}
      <div className="lg:hidden text-center">
        <Link href="/" className="text-2xl font-mono font-bold text-[var(--foreground)]">
          TripStay
        </Link>
      </div>

      {/* Header */}
      <div className="text-center lg:text-left">
        <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Đặt lại mật khẩu</h2>
        <p className="mt-3 text-base text-[var(--muted-foreground)]">
          Tạo mật khẩu mới cho tài khoản của bạn.
        </p>
      </div>

      <Suspense fallback={<ResetPasswordLoading />}>
        <ResetPasswordFormNew />
      </Suspense>
    </div>
  )
}

function ResetPasswordLoading() {
  return (
    <div className="flex items-center justify-center py-12">
      <div className="flex flex-col items-center gap-4">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-[var(--accent)] border-t-transparent" />
        <p className="text-base text-[var(--muted-foreground)]">Đang tải...</p>
      </div>
    </div>
  )
}

export default function ResetPasswordPage() {
  return (
    <AuthLayoutNew>
      <ResetPasswordContent />
    </AuthLayoutNew>
  )
}
