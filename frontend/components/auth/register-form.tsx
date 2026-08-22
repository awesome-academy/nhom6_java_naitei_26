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
import { register as registerUser } from "@/lib/api/auth"
import type { RegisterFormData } from "@/types/auth"

// Validation schema - backend requires password 12-64 chars
const registerSchema = z
  .object({
    fullName: z.string().min(2, "Họ tên phải có ít nhất 2 ký tự").max(150),
    email: z.string().min(1, "Email là bắt buộc").email("Email không hợp lệ"),
    phone: z.string().optional(),
    password: z
      .string()
      .min(12, "Mật khẩu phải có ít nhất 12 ký tự")
      .max(64, "Mật khẩu không được quá 64 ký tự"),
    confirmPassword: z.string().min(1, "Vui lòng nhập lại mật khẩu"),
    terms: z.boolean().refine((val) => val === true, {
      message: "Bạn cần đồng ý với điều khoản để tạo tài khoản",
    }),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Mật khẩu nhập lại chưa khớp",
    path: ["confirmPassword"],
  })

interface RegisterFormProps {
  onSuccess?: () => void
}

export function RegisterForm({ onSuccess }: RegisterFormProps) {
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: "",
      email: "",
      phone: "",
      password: "",
      confirmPassword: "",
      terms: false,
    },
  })

  const onSubmit = async (data: RegisterFormData) => {
    setIsLoading(true)
    setError(null)

    try {
      await registerUser({
        email: data.email,
        password: data.password,
        fullName: data.fullName,
        phone: data.phone,
      })
      toast.success("Tạo tài khoản thành công! Vui lòng kiểm tra email để xác thực.")
      onSuccess?.()
    } catch (err: unknown) {
      const error = err as { status?: number; message?: string }
      if (error.status === 409) {
        setError("Email này đã được sử dụng. Vui lòng sử dụng email khác.")
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
        { id: "login", label: "Đăng nhập", href: "/login" },
        { id: "register", label: "Đăng ký", href: "/register", isActive: true },
      ]}
    >
      <div className="space-y-5">
        <div>
          <h1 className="text-2xl font-bold text-[var(--foreground)]">Tạo tài khoản đặt phòng</h1>
          <p className="mt-1.5 text-sm text-[var(--muted-foreground)]">
            Chỉ cần vài thông tin cơ bản để lưu booking và liên hệ khách sạn.
          </p>
        </div>

        <FormDivider text="hoặc đăng ký bằng email" />

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <FormField label="Họ và tên" htmlFor="fullName" required isInvalid={!!errors.fullName} error={errors.fullName?.message}>
            <Input
              id="fullName"
              type="text"
              placeholder="Nguyễn Minh Anh"
              autoComplete="name"
              {...register("fullName")}
              className={errors.fullName ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
            />
          </FormField>

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

          <FormField label="Số điện thoại" htmlFor="phone" optional isInvalid={!!errors.phone} error={errors.phone?.message}>
            <Input
              id="phone"
              type="tel"
              placeholder="Ví dụ: 090 123 4567"
              autoComplete="tel"
              {...register("phone")}
              className={errors.phone ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
            />
          </FormField>

          <FormField label="Mật khẩu" htmlFor="password" required isInvalid={!!errors.password} error={errors.password?.message}>
            <Input
              id="password"
              type="password"
              placeholder="Tối thiểu 12 ký tự"
              autoComplete="new-password"
              {...register("password")}
              className={errors.password ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
            />
          </FormField>

          <FormField label="Nhập lại mật khẩu" htmlFor="confirmPassword" required isInvalid={!!errors.confirmPassword} error={errors.confirmPassword?.message}>
            <Input
              id="confirmPassword"
              type="password"
              placeholder="Nhập lại mật khẩu"
              autoComplete="new-password"
              {...register("confirmPassword")}
              className={errors.confirmPassword ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]/30" : ""}
            />
          </FormField>

          <div className="pt-1">
            <label className="flex items-start gap-2.5 cursor-pointer">
              <Checkbox
                id="terms"
                {...register("terms")}
                className={`mt-0.5 ${errors.terms ? "border-[var(--destructive)]" : ""}`}
              />
              <span className="text-sm leading-snug text-[var(--muted-foreground)]">
                Tôi đồng ý với{" "}
                <Link href="/terms" className="text-[var(--foreground)] underline underline-offset-2 hover:text-[var(--primary)]">
                  điều khoản đặt phòng
                </Link>{" "}
                và{" "}
                <Link href="/privacy" className="text-[var(--foreground)] underline underline-offset-2 hover:text-[var(--primary)]">
                  chính sách bảo mật
                </Link>
                . <span className="text-[var(--destructive)]">*</span>
              </span>
            </label>
            {errors.terms && (
              <p className="text-xs text-[var(--destructive)] mt-1.5">{errors.terms.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full h-11 text-base" disabled={isLoading}>
            {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Tạo tài khoản
          </Button>

          <FormDivider text="hoặc tiếp tục với" />

          <div className="grid grid-cols-3 gap-3">
            <SocialButton provider="google" onClick={() => toast.info("Tính năng đang được phát triển")} />
            <SocialButton provider="apple" onClick={() => toast.info("Tính năng đang được phát triển")} />
            <SocialButton provider="facebook" onClick={() => toast.info("Tính năng đang được phát triển")} />
          </div>
        </form>

        <p className="text-xs text-[var(--muted-foreground)] leading-relaxed">
          Bằng cách tiếp tục, bạn cho phép TripStay lưu thông tin liên hệ để phục vụ việc đặt phòng. Bạn có thể cập nhật thông tin này trong hồ sơ cá nhân.
        </p>

        <p className="text-center text-sm text-[var(--muted-foreground)]">
          Đã có tài khoản?{" "}
          <Link href="/login" className="font-semibold text-[var(--primary)] hover:underline">
            Đăng nhập
          </Link>
        </p>
      </div>
    </AuthCard>
  )
}