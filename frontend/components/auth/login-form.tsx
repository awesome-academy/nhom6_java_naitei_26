"use client"

import { useState } from "react"
import Link from "next/link"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Checkbox } from "@/components/ui/checkbox"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { toast } from "sonner"
import { Loader2 } from "lucide-react"
import { AuthCard, FormDivider, SocialButton, FormField } from "./auth-card"
import { login } from "@/lib/api/auth"
import type { LoginFormData } from "@/types/auth"

// Validation schema - backend requires 12-64 chars
const loginSchema = z.object({
  email: z.string().min(1, "Email là bắt buộc").email("Email không hợp lệ"),
  password: z.string().min(1, "Mật khẩu là bắt buộc"),
  remember: z.boolean().optional(),
})

interface LoginFormProps {
  onSuccess?: () => void
}

export function LoginForm({ onSuccess }: LoginFormProps) {
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
      remember: false,
    },
  })

  const onSubmit = async (data: LoginFormData) => {
    setIsLoading(true)
    setError(null)

    try {
      await login({ email: data.email, password: data.password })
      toast.success("Đăng nhập thành công")
      onSuccess?.()
    } catch (err: unknown) {
      const error = err as { status?: number; message?: string }
      if (error.status === 401) {
        setError("Email hoặc mật khẩu không đúng")
      } else if (error.status === 423) {
        setError("Tài khoản đang bị khóa tạm thời. Vui lòng thử lại sau.")
      } else if (error.status === 403) {
        setError("Vui lòng xác thực email trước khi đăng nhập")
      } else {
        setError(error.message || "Đã xảy ra lỗi. Vui lòng thử lại.")
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthCard
      tabs={[
        { id: "login", label: "Đăng nhập", href: "/login", isActive: true },
        { id: "register", label: "Đăng ký", href: "/register" },
      ]}
    >
      <div className="space-y-5">
        <div>
          <h1 className="text-2xl font-bold text-[var(--foreground)]">Chào mừng quay lại</h1>
          <p className="mt-1.5 text-sm text-[var(--muted-foreground)]">
            Đăng nhập để tiếp tục booking khách sạn của bạn.
          </p>
        </div>

        <FormDivider text="hoặc dùng email" />

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <FormField label="Email" htmlFor="email" required isInvalid={!!errors.email} error={errors.email?.message}>
            <Input
              id="email"
              type="email"
              placeholder="name@email.com"
              autoComplete="email"
              {...register("email")}
              className={errors.email ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
            />
          </FormField>

          <FormField label="Mật khẩu" htmlFor="password" required isInvalid={!!errors.password} error={errors.password?.message}>
            <Input
              id="password"
              type="password"
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
              {...register("password")}
              className={errors.password ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
            />
          </FormField>

          <div className="flex items-center justify-between pt-1">
            <label className="flex items-center gap-2.5 cursor-pointer">
              <Checkbox id="remember" {...register("remember")} />
              <span className="text-sm text-[var(--muted-foreground)]">
                Ghi nhớ đăng nhập trên thiết bị này
              </span>
            </label>
            <Link
              href="/forgot-password"
              className="text-sm font-mono font-medium text-[var(--primary)] hover:underline"
            >
              Quên mật khẩu?
            </Link>
          </div>

          <Button type="submit" className="w-full h-11 text-base" disabled={isLoading}>
            {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Đăng nhập
          </Button>

          <FormDivider text="hoặc tiếp tục với" />

          <div className="grid grid-cols-3 gap-3">
            <SocialButton provider="google" onClick={() => toast.info("Tính năng đang được phát triển")} />
            <SocialButton provider="apple" onClick={() => toast.info("Tính năng đang được phát triển")} />
            <SocialButton provider="facebook" onClick={() => toast.info("Tính năng đang được phát triển")} />
          </div>
        </form>

        <p className="text-center text-sm text-[var(--muted-foreground)] pt-2">
          Chưa có tài khoản?{" "}
          <Link href="/register" className="font-semibold text-[var(--primary)] hover:underline">
            Tạo tài khoản
          </Link>
        </p>
      </div>
    </AuthCard>
  )
}