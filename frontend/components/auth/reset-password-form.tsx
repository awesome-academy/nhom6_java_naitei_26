"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { resetPassword } from "@/lib/api/auth"
import type { ResetPasswordFormData } from "@/types/auth"
import { toast } from "sonner"
import { Loader2 } from "lucide-react"
import { FormField } from "./auth-card"

// Validation schema - backend requires 12-64 chars
const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(12, "Mật khẩu phải có ít nhất 12 ký tự")
      .max(64, "Mật khẩu không được quá 64 ký tự"),
    confirmPassword: z.string().min(1, "Vui lòng nhập lại mật khẩu"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Mật khẩu nhập lại chưa khớp",
    path: ["confirmPassword"],
  })

interface ResetPasswordFormProps {
  token: string
  onSuccess?: () => void
}

export function ResetPasswordForm({ token, onSuccess }: ResetPasswordFormProps) {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormData>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
  })

  const onSubmit = async (data: ResetPasswordFormData) => {
    setIsLoading(true)
    setError(null)

    try {
      await resetPassword({ token, newPassword: data.password })
      toast.success("Mật khẩu đã được đặt lại thành công")
      onSuccess?.()
      // Redirect to login after 2 seconds
      setTimeout(() => {
        router.push("/login")
      }, 2000)
    } catch (err: unknown) {
      const error = err as { status?: number; message?: string }
      if (error.status === 410) {
        setError("Token đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu đặt lại mật khẩu mới.")
      } else {
        setError(error.message || "Đã xảy ra lỗi. Vui lòng thử lại.")
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <p className="text-sm text-[var(--muted-foreground)]">
        Tạo mật khẩu mới cho tài khoản của bạn. Mật khẩu cần tối thiểu 12 ký tự.
      </p>

      <FormField
        label="Mật khẩu mới"
        htmlFor="password"
        required
        isInvalid={!!errors.password}
        error={errors.password?.message}
      >
        <Input
          id="password"
          type="password"
          placeholder="Tối thiểu 12 ký tự"
          autoComplete="new-password"
          {...register("password")}
          className={errors.password ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
        />
      </FormField>

      <FormField
        label="Nhập lại mật khẩu mới"
        htmlFor="confirmPassword"
        required
        isInvalid={!!errors.confirmPassword}
        error={errors.confirmPassword?.message}
      >
        <Input
          id="confirmPassword"
          type="password"
          placeholder="Nhập lại mật khẩu mới"
          autoComplete="new-password"
          {...register("confirmPassword")}
          className={errors.confirmPassword ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
        />
      </FormField>

      <Button type="submit" className="w-full h-11 text-base" disabled={isLoading}>
        {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
        Đặt lại mật khẩu
      </Button>

      <p className="text-center text-sm text-[var(--muted-foreground)]">
        <Link href="/login" className="font-semibold text-[var(--primary)] hover:underline">
          Quay lại đăng nhập
        </Link>
      </p>
    </form>
  )
}