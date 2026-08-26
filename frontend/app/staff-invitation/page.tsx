"use client"

import { Suspense, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import Link from "next/link"
import { acceptStaffInvitation } from "@/lib/api/auth"
import { AuthLayoutNew } from "@/components/auth/auth-layout-new"
import { Alert, AlertDescription, Button, Input, Label } from "@/components/ui"

function InvitationContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const token = searchParams.get("token")
  const [password, setPassword] = useState("")
  const [confirmation, setConfirmation] = useState("")
  const [error, setError] = useState<string | null>(token ? null : "Liên kết invitation không hợp lệ hoặc đã hết hạn.")
  const [success, setSuccess] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    if (password.length < 12 || password !== confirmation) {
      setError("Mật khẩu phải dài ít nhất 12 ký tự và hai ô phải khớp.")
      return
    }
    setIsSubmitting(true); setError(null)
    try { await acceptStaffInvitation({ token, newPassword: password }); setSuccess(true); window.setTimeout(() => router.push("/login"), 1500) }
    catch (err) { const response = err as { message?: string; status?: number }; setError(response.status === 410 ? "Invitation đã hết hạn hoặc đã được sử dụng." : response.message || "Không thể kích hoạt tài khoản Staff.") }
    finally { setIsSubmitting(false) }
  }

  return <div className="w-full max-w-md space-y-6"><div><h1 className="text-3xl font-semibold">Kích hoạt tài khoản Staff</h1><p className="mt-2 text-muted-foreground">Xác thực email và đặt mật khẩu đăng nhập chính thức.</p></div>{error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}{success ? <Alert><AlertDescription>Kích hoạt thành công. Đang chuyển tới trang đăng nhập...</AlertDescription></Alert> : <form onSubmit={submit} className="space-y-5"><div className="grid gap-2"><Label htmlFor="staff-invitation-password">Mật khẩu mới</Label><Input id="staff-invitation-password" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tối thiểu 12 ký tự" /></div><div className="grid gap-2"><Label htmlFor="staff-invitation-confirm">Nhập lại mật khẩu</Label><Input id="staff-invitation-confirm" type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} /></div><Button className="w-full" type="submit" disabled={isSubmitting || !token}>{isSubmitting ? "Đang kích hoạt..." : "Kích hoạt tài khoản"}</Button><p className="text-center text-sm text-muted-foreground"><Link className="underline" href="/login">Quay lại đăng nhập</Link></p></form>}</div>
}

export default function StaffInvitationPage() {
  return <AuthLayoutNew><Suspense fallback={<div>Đang tải...</div>}><InvitationContent /></Suspense></AuthLayoutNew>
}
