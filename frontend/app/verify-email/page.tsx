"use client"

import { Suspense, useEffect, useRef, useState } from "react"
import { useSearchParams } from "next/navigation"
import Link from "next/link"
import { verifyEmail } from "@/lib/api/auth"
import { Button } from "@/components/ui/button"
import { Loader2 } from "lucide-react"

type VerificationResult = {
  token: string
  status: "success" | "error"
  message: string
  isExpired?: boolean
}

function VerifyEmailContent() {
  const searchParams = useSearchParams()
  const token = searchParams.get("token")
  const [verificationResult, setVerificationResult] = useState<VerificationResult | null>(null)
  const attemptedTokenRef = useRef<string | null>(null)

  useEffect(() => {
    if (!token) return

    // Next/React development mode may run effects twice. A verification token is
    // single-use, so keep one request per token to avoid a false error on the
    // second invocation.
    if (attemptedTokenRef.current === token) return
    attemptedTokenRef.current = token

    const verify = async () => {
      try {
        const response = await verifyEmail({ token })
        setVerificationResult({
          token,
          status: "success",
          message: response.message,
        })
      } catch (err: unknown) {
        const error = err as { status?: number; message?: string }
        if (error.status === 410) {
          setVerificationResult({
            token,
            status: "error",
            isExpired: true,
            message: "Link xác thực đã hết hạn. Vui lòng đăng nhập để gửi lại email xác thực.",
          })
        } else {
          setVerificationResult({
            token,
            status: "error",
            message: error.message || "Đã xảy ra lỗi khi xác thực email.",
          })
        }
      }
    }

    void verify()
  }, [token])

  const hasResultForCurrentToken = token !== null && verificationResult?.token === token
  const status: "loading" | "success" | "error" = !token
    ? "error"
    : hasResultForCurrentToken
      ? verificationResult.status
      : "loading"
  const message = !token
    ? "Token xác thực không hợp lệ hoặc đã hết hạn."
    : hasResultForCurrentToken
      ? verificationResult.message
      : ""
  const isVerificationTokenExpired = hasResultForCurrentToken && verificationResult.isExpired === true

  // Loading state
  if (status === "loading") {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-6 text-center">
        <div className="relative">
          <Loader2 className="h-20 w-20 animate-spin text-[var(--accent)]" />
        </div>
        <div className="space-y-2">
          <h2 className="font-serif text-2xl font-medium text-[var(--foreground)]">
            Đang xác thực email...
          </h2>
          <p className="text-base text-[var(--muted-foreground)]">
            Vui lòng đợi trong giây lát
          </p>
        </div>
      </div>
    )
  }

  // Success state - centered success page
  if (status === "success") {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-8 text-center">
        {/* Success Icon - Centered */}
        <div className="relative">
          <div className="absolute inset-0 bg-green-100 rounded-full scale-150 opacity-50 blur-xl" />
          <div className="relative rounded-full bg-green-100 p-6">
            <svg className="h-20 w-20 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
        </div>

        {/* Success Message */}
        <div className="space-y-3">
          <h2 className="font-serif text-3xl font-medium text-[var(--foreground)]">
            Cảm ơn bạn đã xác nhận email!
          </h2>
          <p className="text-lg text-[var(--muted-foreground)]">
            Tài khoản của bạn đã được kích hoạt thành công.
          </p>
          <p className="text-base text-[var(--muted-foreground)]">
            Bây giờ bạn có thể đăng nhập để trải nghiệm dịch vụ của TripStay.
          </p>
        </div>

        {/* Action Button */}
        <div className="pt-4">
          <Button asChild className="h-12 px-8 text-base font-medium">
            <Link href="/login">
              Đăng nhập ngay
            </Link>
          </Button>
        </div>
      </div>
    )
  }

  // Error state
  return (
    <div className="space-y-8">
      {/* Mobile Logo */}
      <div className="lg:hidden text-center">
        <Link href="/" className="text-2xl font-mono font-bold text-[var(--foreground)]">
          TripStay
        </Link>
      </div>

      {/* Error Content */}
      <div className="text-center lg:text-left">
        <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">
          Xác thực thất bại
        </h2>
        <p className="mt-3 text-base text-[var(--muted-foreground)]">
          {message}
        </p>
      </div>

      {/* Error Icon */}
      <div className="flex justify-center py-4">
        <div className="rounded-full bg-red-100 p-5">
          <svg className="h-14 w-14 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
      </div>

      {/* Error Details */}
      <div className="rounded-lg bg-red-50 border border-red-200 p-4">
        <p className="text-sm text-red-800">
          {message}
        </p>
      </div>

      {/* Action Buttons */}
      <div className="space-y-3">
        <Button asChild className="w-full h-12 text-base font-medium">
          <Link href="/login">Quay lại đăng nhập</Link>
        </Button>
        {!isVerificationTokenExpired && (
          <Button asChild variant="outline" className="w-full h-12 text-base font-medium">
            <Link href="/register">Đăng ký tài khoản mới</Link>
          </Button>
        )}
      </div>
    </div>
  )
}

function VerifyEmailLoading() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[400px] space-y-6">
      <Loader2 className="h-16 w-16 animate-spin text-[var(--accent)]" />
      <p className="text-base text-[var(--muted-foreground)]">Đang tải...</p>
    </div>
  )
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<VerifyEmailLoading />}>
      <VerifyEmailContent />
    </Suspense>
  )
}
