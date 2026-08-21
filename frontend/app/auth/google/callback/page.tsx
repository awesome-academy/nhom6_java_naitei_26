"use client"

import { Suspense, useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"
import Link from "next/link"
import { Loader2 } from "lucide-react"
import { storeTokens } from "@/lib/api/auth"
import { useAuth } from "@/lib/auth-context"
import { useRouter } from "next/navigation"

function GoogleOAuthCallbackContent() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const { setAuth } = useAuth()
  const [status, setStatus] = useState<"loading" | "success" | "error">("loading")
  const [errorMessage, setErrorMessage] = useState("")

  useEffect(() => {
    const processCallback = async () => {
      // Check for error from redirect
      const error = searchParams.get("error")
      if (error) {
        setStatus("error")
        setErrorMessage(error === "access_denied"
          ? "Bạn đã hủy đăng nhập Google."
          : `Đăng nhập Google thất bại: ${decodeURIComponent(error)}`)
        return
      }

      // Check for auth data from backend redirect
      const data = searchParams.get("data")
      if (!data) {
        setStatus("error")
        setErrorMessage("Không nhận được thông tin đăng nhập. Vui lòng thử lại.")
        return
      }

      try {
        // Parse auth data from URL (Next.js auto-decodes URL params)
        const decoded = JSON.parse(data)

        if (!decoded.accessToken || !decoded.refreshToken || !decoded.user) {
          throw new Error("Invalid auth data format")
        }

        const { accessToken, refreshToken, user } = decoded

        // Store tokens
        storeTokens(accessToken, refreshToken)

        // Set auth context
        setAuth(user, accessToken, refreshToken)

        setStatus("success")

        // Redirect after showing success message
        setTimeout(() => {
          if (user.roles && (user.roles.includes("ADMIN") || user.roles.includes("STAFF"))) {
            router.push("/admin")
          } else {
            router.push("/")
          }
          router.refresh()
        }, 1500)
      } catch (err) {
        console.error("OAuth callback error:", err)
        setStatus("error")
        setErrorMessage("Đăng nhập Google thất bại. Vui lòng thử lại.")
      }
    }

    processCallback()
  }, [searchParams, setAuth, router])

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--background)]">
      <div className="w-full max-w-md p-8 text-center">
        {status === "loading" && (
          <>
            <Loader2 className="h-16 w-16 animate-spin text-[var(--accent)] mx-auto mb-6" />
            <h2 className="text-2xl font-serif font-medium text-[var(--foreground)] mb-2">
              Đang đăng nhập...
            </h2>
            <p className="text-[var(--muted-foreground)]">
              Vui lòng đợi trong giây lát
            </p>
          </>
        )}

        {status === "success" && (
          <>
            <div className="w-16 h-16 rounded-full bg-green-100 mx-auto mb-6 flex items-center justify-center">
              <svg className="h-10 w-10 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className="text-2xl font-serif font-medium text-[var(--foreground)] mb-2">
              Đăng nhập thành công!
            </h2>
            <p className="text-[var(--muted-foreground)]">
              Đang chuyển hướng...
            </p>
          </>
        )}

        {status === "error" && (
          <>
            <div className="w-16 h-16 rounded-full bg-red-100 mx-auto mb-6 flex items-center justify-center">
              <svg className="h-10 w-10 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className="text-2xl font-serif font-medium text-[var(--foreground)] mb-2">
              Đăng nhập thất bại
            </h2>
            <p className="text-[var(--muted-foreground)] mb-6">
              {errorMessage}
            </p>
            <Link
              href="/login"
              className="inline-flex items-center justify-center h-12 px-6 text-base font-medium bg-[var(--accent)] text-white rounded-md hover:opacity-90 transition-opacity"
            >
              Quay lại trang đăng nhập
            </Link>
          </>
        )}
      </div>
    </div>
  )
}

function LoadingFallback() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--background)]">
      <Loader2 className="h-16 w-16 animate-spin text-[var(--accent)]" />
    </div>
  )
}

export default function GoogleOAuthCallbackPage() {
  return (
    <Suspense fallback={<LoadingFallback />}>
      <GoogleOAuthCallbackContent />
    </Suspense>
  )
}
