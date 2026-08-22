"use client"

import { useState } from "react"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { toast } from "sonner"
import { Loader2, Mail } from "lucide-react"
import { requestPasswordReset } from "@/lib/api/auth"
import type { ForgotPasswordFormData } from "@/types/auth"

const forgotPasswordSchema = z.object({
  email: z.string().min(1, "Email là bắt buộc").email("Email không hợp lệ"),
})

interface ForgotPasswordFormNewProps {
  onSuccess?: () => void
}

export function ForgotPasswordFormNew({ onSuccess }: ForgotPasswordFormNewProps) {
  const [isLoading, setIsLoading] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: "",
    },
  })

  const onSubmit = async (data: ForgotPasswordFormData) => {
    setIsLoading(true)
    setError(null)

    try {
      await requestPasswordReset({ email: data.email })
      setIsSuccess(true)
      toast.success("Đã gửi hướng dẫn khôi phục mật khẩu qua email")
      onSuccess?.()
    } catch (err: unknown) {
      setError("Đã xảy ra lỗi. Vui lòng thử lại.")
    } finally {
      setIsLoading(false)
    }
  }

  if (isSuccess) {
    return (
      <div className="space-y-6 text-center">
        <div className="flex justify-center">
          <div className="rounded-full bg-[var(--accent)]/10 p-5">
            <Mail className="h-12 w-12 text-[var(--accent)]" />
          </div>
        </div>
        <div>
          <h3 className="font-serif text-2xl font-medium text-[var(--foreground)]">Kiểm tra email của bạn</h3>
          <p className="mt-3 text-base text-[var(--muted-foreground)]">
            Chúng tôi đã gửi hướng dẫn khôi phục mật khẩu. Vui lòng kiểm tra hộp thư và làm theo hướng dẫn.
          </p>
        </div>
        <div className="rounded-lg border border-[var(--border)] bg-[var(--muted)]/30 p-5 text-left">
          <strong className="block mb-2 text-[var(--foreground)]">Sau khi nhận email</strong>
          <p className="text-sm text-[var(--muted-foreground)]">
            Nhấp vào liên kết trong email để đặt lại mật khẩu. Liên kết có hiệu lực trong thời gian giới hạn.
          </p>
        </div>
        <div className="space-y-2">
          <p className="text-sm text-[var(--muted-foreground)]">
            Không nhận được email?{" "}
            <button
              type="button"
              onClick={() => setIsSuccess(false)}
              className="font-medium text-[var(--accent)] hover:underline"
            >
              Thử lại
            </button>
          </p>
          <Link href="/login" className="block text-sm font-medium text-[var(--accent)] hover:underline">
            Quay lại đăng nhập
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div className="space-y-3">
          <Label htmlFor="email" className="text-sm font-medium">
            Email <span className="text-[var(--destructive)]">*</span>
          </Label>
          <div className="relative">
            <Mail className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="email"
              type="email"
              placeholder="name@email.com"
              autoComplete="email"
              {...register("email")}
              className={`pl-11 ${errors.email ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]" : ""}`}
            />
          </div>
          {errors.email && (
            <p className="text-xs text-[var(--destructive)]">{errors.email.message}</p>
          )}
        </div>

        <div className="rounded-lg border border-[var(--border)] bg-[var(--muted)]/30 p-4 text-sm text-left">
          <strong className="block mb-1 text-[var(--foreground)]">Sau khi gửi</strong>
          <p className="text-[var(--muted-foreground)]">
            Hệ thống sẽ gửi email hướng dẫn đặt lại mật khẩu vào hộp thư của bạn.
          </p>
        </div>

        <Button type="submit" className="w-full h-12 text-base font-medium bg-[var(--accent)] hover:bg-[var(--accent)]/90" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Gửi hướng dẫn qua email
        </Button>

        <p className="text-center text-base text-[var(--muted-foreground)]">
          Nhớ mật khẩu rồi?{" "}
          <Link href="/login" className="font-medium text-[var(--accent)] hover:underline">
            Quay lại đăng nhập
          </Link>
        </p>
      </form>
    </div>
  )
}
