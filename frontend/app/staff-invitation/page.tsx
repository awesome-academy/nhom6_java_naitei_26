"use client"

import { Suspense, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import Link from "next/link"
import { acceptStaffInvitation } from "@/lib/api/auth"
import { AuthLayoutNew } from "@/components/auth/auth-layout-new"
import { Alert, AlertDescription, Button } from "@/components/ui"

function InvitationContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const token = searchParams.get("token")
  const [error, setError] = useState<string | null>(token ? null : "Liên kết invitation không hợp lệ hoặc đã hết hạn.")
  const [success, setSuccess] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setIsSubmitting(true)
    setError(null)
    try {
      await acceptStaffInvitation({ token })
      setSuccess(true)
      window.setTimeout(() => router.push("/manager/login"), 1200)
    } catch (err) {
      const response = err as { message?: string; status?: number }
      setError(response.status === 410
        ? "Invitation đã hết hạn hoặc đã được sử dụng."
        : response.message || "Không thể kích hoạt tài khoản Staff.")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="w-full max-w-md space-y-6">
      <div>
        <h1 className="text-3xl font-semibold">Kích hoạt tài khoản Staff</h1>
        <p className="mt-2 text-muted-foreground">
          Xác thực email để kích hoạt tài khoản. Mật khẩu đăng nhập đã được gửi trong email invitation.
        </p>
      </div>
      {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
      {success ? (
        <Alert><AlertDescription>Kích hoạt thành công. Đang chuyển tới trang đăng nhập Staff...</AlertDescription></Alert>
      ) : (
        <form onSubmit={submit} className="space-y-5">
          <Button className="w-full" type="submit" disabled={isSubmitting || !token}>
            {isSubmitting ? "Đang kích hoạt..." : "Kích hoạt tài khoản"}
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            <Link className="underline" href="/manager/login">Quay lại đăng nhập Staff</Link>
          </p>
        </form>
      )}
    </div>
  )
}

export default function StaffInvitationPage() {
  return <AuthLayoutNew><Suspense fallback={<div>Đang tải...</div>}><InvitationContent /></Suspense></AuthLayoutNew>
}
