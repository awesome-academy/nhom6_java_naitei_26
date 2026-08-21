"use client"

import { useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/Button"
import { Input } from "@/components/ui/Input"
import { Label } from "@/components/ui/Label"
import { Alert, AlertDescription } from "@/components/ui/Alert"
import { resetPassword } from "@/lib/api/auth"
import type { ResetPasswordFormData } from "@/types/auth"
import { toast } from "sonner"
import { Loader2, Lock, Eye, EyeOff } from "lucide-react"

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

export function ResetPasswordFormNew() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const token = searchParams.get("token")

  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

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

  if (!token) {
    return (
      <div className="space-y-6 text-center">
        <div className="rounded-full bg-red-100 p-5 mx-auto w-fit">
          <Alert variant="destructive" className="border-0 bg-transparent p-0">
            <AlertDescription className="text-base">
              Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.
            </AlertDescription>
          </Alert>
        </div>
        <Link href="/forgot-password" className="block text-base font-medium text-[var(--accent)] hover:underline">
          Yêu cầu đặt lại mật khẩu mới
        </Link>
      </div>
    )
  }

  const onSubmit = async (data: ResetPasswordFormData) => {
    setIsLoading(true)
    setError(null)

    try {
      await resetPassword({ token, newPassword: data.password })
      toast.success("Mật khẩu đã được đặt lại thành công")
      setTimeout(() => {
        router.push("/login")
      }, 1500)
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
    <div className="space-y-6">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div className="space-y-3">
          <Label htmlFor="password" className="text-sm font-medium">
            Mật khẩu mới <span className="text-[var(--destructive)]">*</span>
          </Label>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="password"
              type={showPassword ? "text" : "password"}
              placeholder="Tối thiểu 12 ký tự"
              autoComplete="new-password"
              {...register("password")}
              className={`pl-11 pr-11 ${errors.password ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]" : ""}`}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {errors.password && (
            <p className="text-xs text-[var(--destructive)]">{errors.password.message}</p>
          )}
        </div>

        <div className="space-y-3">
          <Label htmlFor="confirmPassword" className="text-sm font-medium">
            Nhập lại mật khẩu mới <span className="text-[var(--destructive)]">*</span>
          </Label>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="confirmPassword"
              type={showConfirmPassword ? "text" : "password"}
              placeholder="Nhập lại mật khẩu mới"
              autoComplete="new-password"
              {...register("confirmPassword")}
              className={`pl-11 pr-11 ${errors.confirmPassword ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]" : ""}`}
            />
            <button
              type="button"
              onClick={() => setShowConfirmPassword(!showConfirmPassword)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
            >
              {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {errors.confirmPassword && (
            <p className="text-xs text-[var(--destructive)]">{errors.confirmPassword.message}</p>
          )}
        </div>

        <Button type="submit" className="w-full h-12 text-base font-medium" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Đặt lại mật khẩu
        </Button>

        <p className="text-center text-base text-[var(--muted-foreground)]">
          <Link href="/login" className="font-medium text-[var(--accent)] hover:underline">
            Quay lại đăng nhập
          </Link>
        </p>
      </form>
    </div>
  )
}
