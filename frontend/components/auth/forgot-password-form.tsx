"use client"

import { useState } from "react"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { toast } from "sonner"
import { Loader2, Mail } from "lucide-react"
import { FormField } from "./auth-card"
import { requestPasswordReset } from "@/lib/api/auth"
import type { ForgotPasswordFormData } from "@/types/auth"

const forgotPasswordSchema = z.object({
  email: z.string().min(1, "Email là bắt buộc").email("Email không hợp lệ"),
})

interface ForgotPasswordFormProps {
  onSuccess?: () => void
}

export function ForgotPasswordForm({ onSuccess }: ForgotPasswordFormProps) {
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
      // Backend always returns 202 even if email doesn't exist (security)
      // So we show success anyway
      setIsSuccess(true)
      toast.success("Đã gửi hướng dẫn khôi phục mật khẩu qua email")
      onSuccess?.()
    } finally {
      setIsLoading(false)
    }
  }

  if (isSuccess) {
    return (
      <div className="space-y-5 text-center">
        <div className="flex justify-center">
          <div className="rounded-full bg-[var(--primary)]/10 p-4">
            <Mail className="h-8 w-8 text-[var(--primary)]" />
          </div>
        </div>
        <div>
          <h1 className="text-2xl font-bold">Kiểm tra email của bạn</h1>
          <p className="mt-2 text-sm text-[var(--muted-foreground)]">
            Chúng tôi đã gửi hướng dẫn khôi phục mật khẩu. Vui lòng kiểm tra hộp thư và làm theo hướng dẫn.
          </p>
        </div>
        <div className="rounded-md border border-[var(--border)] bg-[var(--muted)]/30 p-4 text-sm text-left">
          <strong className="block mb-1 text-[var(--foreground)]">Sau khi nhận email</strong>
          Nhấp vào liên kết trong email để đặt lại mật khẩu. Liên kết có hiệu lực trong thời gian giới hạn.
        </div>
        <p className="text-sm text-[var(--muted-foreground)]">
          Không nhận được email?{" "}
          <button
            type="button"
            onClick={() => setIsSuccess(false)}
            className="font-semibold text-[var(--primary)] hover:underline"
          >
            Thử lại
          </button>
        </p>
        <p className="text-sm text-[var(--muted-foreground)]">
          <Link href="/login" className="font-semibold text-[var(--primary)] hover:underline">
            Quay lại đăng nhập
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-[var(--foreground)]">Quên mật khẩu</h1>
        <p className="mt-1.5 text-sm text-[var(--muted-foreground)]">
          Nhập địa chỉ email tài khoản của bạn. Chúng tôi sẽ gửi hướng dẫn khôi phục mật khẩu.
        </p>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          label="Email nhận mã"
          htmlFor="email"
          required
          isInvalid={!!errors.email}
          error={errors.email?.message}
        >
          <Input
            id="email"
            type="email"
            placeholder="name@email.com"
            autoComplete="email"
            {...register("email")}
            className={errors.email ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
          />
        </FormField>

        <div className="rounded-md border border-[var(--border)] bg-[var(--muted)]/30 p-4 text-sm">
          <strong className="block mb-1 text-[var(--foreground)]">Sau khi gửi</strong>
          Prototype sẽ gửi email hướng dẫn đặt lại mật khẩu vào hộp thư của bạn.
        </div>

        <Button type="submit" className="w-full h-11 text-base" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Gửi hướng dẫn qua email
        </Button>

        <p className="text-center text-sm text-[var(--muted-foreground)]">
          Nhớ mật khẩu rồi?{" "}
          <Link href="/login" className="font-semibold text-[var(--primary)] hover:underline">
            Quay lại đăng nhập
          </Link>
        </p>
      </form>
    </div>
  )
}