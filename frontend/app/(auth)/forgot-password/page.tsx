import Link from "next/link"
import { ForgotPasswordFormNew } from "@/components/auth/forgot-password-form-new"

export default function ForgotPasswordPage() {
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
        <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Quên mật khẩu</h2>
        <p className="mt-3 text-base text-[var(--muted-foreground)]">
          Nhập địa chỉ email tài khoản của bạn. Chúng tôi sẽ gửi hướng dẫn khôi phục mật khẩu.
        </p>
      </div>

      <ForgotPasswordFormNew />
    </div>
  )
}
