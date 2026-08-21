"use client"

import { Suspense, useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"
import Link from "next/link"
import { verifyEmail } from "@/lib/api/auth"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Loader2, CheckCircle, XCircle } from "lucide-react"
import { AuthLayoutNew } from "@/components/auth/auth-layout-new"

function VerifyEmailContent() {
  const searchParams = useSearchParams()
  const token = searchParams.get("token")
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    token ? "loading" : "error"
  )
  const [message, setMessage] = useState(
    token ? "" : "Token xác thực không hợp lệ hoặc đã hết hạn."
  )

  useEffect(() => {
    if (!token) return

    const verify = async () => {
      try {
        const response = await verifyEmail({ token })
        setStatus("success")
        setMessage(response.message)
      } catch (err: unknown) {
        setStatus("error")
        const error = err as { status?: number; message?: string }
        if (error.status === 410) {
          setMessage("Token đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu gửi lại email xác thực.")
        } else {
          setMessage(error.message || "Đã xảy ra lỗi khi xác thực email.")
        }
      }
    }

    verify()
  }, [token])

  return (
    <div className="w-full max-w-md space-y-8">
      {/* Mobile Logo */}
      <div className="lg:hidden text-center">
        <Link href="/" className="text-2xl font-mono font-bold text-[var(--foreground)]">
          TripStay
        </Link>
      </div>

      {/* Content */}
      <div className="space-y-8 text-center">
        {status === "loading" && (
          <>
            <div className="flex justify-center">
              <Loader2 className="h-16 w-16 animate-spin text-[var(--accent)]" />
            </div>
            <div>
              <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Đang xác thực email...</h2>
              <p className="mt-3 text-base text-[var(--muted-foreground)]">Vui lòng đợi trong giây lát</p>
            </div>
          </>
        )}

        {status === "success" && (
          <>
            <div className="flex justify-center">
              <div className="rounded-full bg-green-100 p-5">
                <CheckCircle className="h-14 w-14 text-green-600" />
              </div>
            </div>
            <div>
              <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Xác thực thành công!</h2>
              <p className="mt-3 text-base text-[var(--muted-foreground)]">
                {message}
              </p>
            </div>
            <Button asChild className="w-full h-12 text-base font-medium">
              <Link href="/login">Đăng nhập ngay</Link>
            </Button>
          </>
        )}

        {status === "error" && (
          <>
            <div className="flex justify-center">
              <div className="rounded-full bg-red-100 p-5">
                <XCircle className="h-14 w-14 text-red-600" />
              </div>
            </div>
            <div>
              <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Xác thực thất bại</h2>
              <p className="mt-3 text-base text-[var(--muted-foreground)]">
                {message}
              </p>
            </div>
            <Alert variant="destructive" className="text-left">
              <AlertDescription>{message}</AlertDescription>
            </Alert>
            <Button asChild variant="outline" className="w-full h-12 text-base font-medium">
              <Link href="/register">Đăng ký tài khoản mới</Link>
            </Button>
          </>
        )}
      </div>
    </div>
  )
}

function VerifyEmailLoading() {
  return (
    <div className="w-full max-w-md space-y-8">
      <div className="lg:hidden text-center">
        <Link href="/" className="text-2xl font-mono font-bold text-[var(--foreground)]">
          TripStay
        </Link>
      </div>
      <div className="flex flex-col items-center gap-4 py-12">
        <Loader2 className="h-16 w-16 animate-spin text-[var(--accent)]" />
        <p className="text-base text-[var(--muted-foreground)]">Đang tải...</p>
      </div>
    </div>
  )
}

export default function VerifyEmailPage() {
  return (
    <AuthLayoutNew>
      <Suspense fallback={<VerifyEmailLoading />}>
        <VerifyEmailContent />
      </Suspense>
    </AuthLayoutNew>
  )
}
