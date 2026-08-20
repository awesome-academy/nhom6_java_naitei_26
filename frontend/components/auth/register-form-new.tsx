"use client"

import { useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useForm, Controller } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { toast } from "sonner"
import { Loader2, Mail, Lock, User, Phone, Eye, EyeOff } from "lucide-react"
import { register as registerUser } from "@/lib/api/auth"
import type { RegisterFormData } from "@/types/auth"

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

export function RegisterFormNew() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const {
    register,
    control,
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
      router.push("/login")
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
    <div className="space-y-8">
      {/* Mobile Logo */}
      <div className="lg:hidden text-center mb-8">
        <Link href="/" className="text-2xl font-mono font-bold text-[var(--foreground)]">
          TripStay
        </Link>
      </div>

      {/* Header */}
      <div className="text-center lg:text-left">
        <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Tạo tài khoản</h2>
        <p className="mt-3 text-base text-[var(--muted-foreground)]">
          Đã có tài khoản?{" "}
          <Link href="/login" className="font-medium text-[var(--accent)] hover:underline">
            Đăng nhập ngay
          </Link>
        </p>
      </div>

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Form */}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        {/* Full Name */}
        <div className="space-y-3">
          <Label htmlFor="fullName" className="text-sm font-medium">
            Họ và tên <span className="text-[var(--destructive)]">*</span>
          </Label>
          <div className="relative">
            <User className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="fullName"
              type="text"
              placeholder="Nguyễn Minh Anh"
              autoComplete="name"
              {...register("fullName")}
              className={`pl-11 ${errors.fullName ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]" : ""}`}
            />
          </div>
          {errors.fullName && (
            <p className="text-xs text-[var(--destructive)]">{errors.fullName.message}</p>
          )}
        </div>

        {/* Email */}
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

        {/* Phone */}
        <div className="space-y-3">
          <Label htmlFor="phone" className="text-sm font-medium">
            Số điện thoại <span className="text-[var(--muted-foreground)] font-normal">(không bắt buộc)</span>
          </Label>
          <div className="relative">
            <Phone className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="phone"
              type="tel"
              placeholder="090 123 4567"
              autoComplete="tel"
              {...register("phone")}
              className={`pl-11 ${errors.phone ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]" : ""}`}
            />
          </div>
          {errors.phone && (
            <p className="text-xs text-[var(--destructive)]">{errors.phone.message}</p>
          )}
        </div>

        {/* Password */}
        <div className="space-y-3">
          <Label htmlFor="password" className="text-sm font-medium">
            Mật khẩu <span className="text-[var(--destructive)]">*</span>
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

        {/* Confirm Password */}
        <div className="space-y-3">
          <Label htmlFor="confirmPassword" className="text-sm font-medium">
            Nhập lại mật khẩu <span className="text-[var(--destructive)]">*</span>
          </Label>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="confirmPassword"
              type={showConfirmPassword ? "text" : "password"}
              placeholder="Nhập lại mật khẩu"
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

        {/* Terms */}
        <div className="flex items-start gap-3">
          <Controller
            name="terms"
            control={control}
            render={({ field }) => (
              <Checkbox
                id="terms"
                checked={field.value}
                onCheckedChange={(checked) => field.onChange(checked === true)}
                className={`mt-0.5 ${errors.terms ? "border-[var(--destructive)]" : ""}`}
              />
            )}
          />
          <Label htmlFor="terms" className="text-sm font-normal leading-snug cursor-pointer">
            Tôi đồng ý với{" "}
            <Link href="/terms" className="text-[var(--accent)] underline underline-offset-2 hover:text-[var(--foreground)]">
              Điều khoản dịch vụ
            </Link>{" "}
            và{" "}
            <Link href="/privacy" className="text-[var(--accent)] underline underline-offset-2 hover:text-[var(--foreground)]">
              Chính sách bảo mật
            </Link>
            . <span className="text-[var(--destructive)]">*</span>
          </Label>
        </div>
        {errors.terms && (
          <p className="text-xs text-[var(--destructive)]">{errors.terms.message}</p>
        )}

        {/* Submit */}
        <Button type="submit" className="w-full h-12 text-base font-medium" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Tạo tài khoản
        </Button>
      </form>

      {/* Divider */}
      <div className="relative">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-[var(--border)]" />
        </div>
        <div className="relative flex justify-center">
          <span className="bg-[var(--background)] px-4 text-sm text-[var(--muted-foreground)]">
            hoặc tiếp tục với
          </span>
        </div>
      </div>

      {/* Social Register */}
      <div className="grid grid-cols-3 gap-4">
        <Button variant="outline" className="h-12" onClick={() => toast.info("Tính năng đang phát triển")}>
          <svg className="h-5 w-5" viewBox="0 0 24 24">
            <path d="M22.6 12.25c0-.74-.07-1.45-.19-2.12H12v4.02h5.95a5.08 5.08 0 0 1-2.2 3.33v2.76h3.56c2.08-1.92 3.29-4.75 3.29-7.99Z" fill="#4285F4" />
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.56-2.76c-.98.66-2.23 1.05-3.72 1.05-2.86 0-5.29-1.93-6.16-4.53H2.18v2.85A11 11 0 0 0 12 23Z" fill="#34A853" />
            <path d="M5.84 14.1A6.6 6.6 0 0 1 5.5 12c0-.73.12-1.43.34-2.1V7.05H2.18A11 11 0 0 0 1 12c0 1.77.42 3.44 1.18 4.95l3.66-2.85Z" fill="#FBBC05" />
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.05L5.84 9.9C6.71 7.31 9.14 5.38 12 5.38Z" fill="#EA4335" />
          </svg>
        </Button>
        <Button variant="outline" className="h-12" onClick={() => toast.info("Tính năng đang phát triển")}>
          <svg className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
            <path d="M16.36 12.91c-.02-2.19 1.79-3.24 1.87-3.29-1.02-1.49-2.6-1.69-3.16-1.72-1.34-.14-2.61.79-3.29.79-.68 0-1.73-.77-2.84-.75-1.46.02-2.8.85-3.55 2.16-1.52 2.64-.39 6.55 1.09 8.69.72 1.04 1.58 2.22 2.71 2.17 1.09-.04 1.5-.7 2.81-.7 1.31 0 1.68.7 2.83.68 1.17-.02 1.91-1.06 2.63-2.1.83-1.21 1.17-2.38 1.19-2.44-.03-.01-2.27-.87-2.29-3.49Zm-2.17-6.42c.6-.73 1.01-1.75.9-2.76-.87.04-1.93.58-2.55 1.31-.56.65-1.05 1.69-.92 2.69.98.08 1.96-.49 2.57-1.24Z" />
          </svg>
        </Button>
        <Button variant="outline" className="h-12" onClick={() => toast.info("Tính năng đang phát triển")}>
          <svg className="h-5 w-5 text-[#1877F2]" viewBox="0 0 24 24" fill="currentColor">
            <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
          </svg>
        </Button>
      </div>
    </div>
  )
}
