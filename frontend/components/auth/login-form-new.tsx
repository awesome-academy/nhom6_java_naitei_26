"use client"

import { useState, useEffect, useMemo } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { toast } from "sonner"
import { Loader2, Mail, Lock, Eye, EyeOff } from "lucide-react"
import { login, getGoogleOAuthUrl } from "@/lib/api/auth"
import { useAuth } from "@/lib/auth-context"
import type { LoginFormData } from "@/types/auth"

const loginSchema = z.object({
  email: z.string().min(1, "Email là bắt buộc").email("Email không hợp lệ"),
  password: z.string().min(1, "Mật khẩu là bắt buộc"),
  remember: z.boolean().optional(),
})

export function LoginFormNew() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setAuth } = useAuth()
  const [isLoading, setIsLoading] = useState(false)
  const [isGoogleLoading, setIsGoogleLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)

  // Memoize search params to prevent unnecessary re-renders
  const pendingEmail = useMemo(() => searchParams.get("email") || "", [searchParams])
  const isPendingVerification = useMemo(() => searchParams.get("pending") === "1", [searchParams])

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

  // Set initial email value after mount
  useEffect(() => {
    if (pendingEmail && !isPendingVerification) {
      // Only pre-fill if coming from registration
    }
  }, [pendingEmail, isPendingVerification])

  // Handle Google OAuth login
  const handleGoogleLogin = async () => {
    setIsGoogleLoading(true)
    setError(null)

    try {
      const { authorizationUrl } = await getGoogleOAuthUrl()
      // Redirect to Google OAuth
      window.location.href = authorizationUrl
    } catch (err) {
      const error = err as { message?: string }
      setError(error.message || "Không thể khởi tạo đăng nhập Google. Vui lòng thử lại.")
      setIsGoogleLoading(false)
    }
  }

  const onSubmit = async (data: LoginFormData) => {
    setIsLoading(true)
    setError(null)

    try {
      const response = await login({ email: data.email, password: data.password })
      setAuth(response.user, response.accessToken, response.refreshToken)
      toast.success("Đăng nhập thành công")
      // Redirect based on role
      if (response.user.roles.includes("ADMIN") || response.user.roles.includes("STAFF")) {
        router.push("/admin")
      } else {
        router.push("/")
      }
      router.refresh()
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
    <div className="space-y-8">
      {/* Mobile Logo */}
      <div className="lg:hidden text-center mb-8">
        <Link href="/" className="text-2xl font-mono font-bold text-[var(--foreground)]">
          TripStay
        </Link>
      </div>

      {/* Header */}
      <div className="text-center lg:text-left">
        <h2 className="font-serif text-3xl font-medium text-[var(--foreground)] sm:text-4xl">Đăng nhập</h2>
        <p className="mt-3 text-base text-[var(--muted-foreground)]">
          Chưa có tài khoản?{" "}
          <Link href="/register" className="font-medium text-[var(--accent)] hover:underline">
            Đăng ký ngay
          </Link>
        </p>
      </div>

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Pending Verification Alert */}
      {isPendingVerification && pendingEmail && (
        <div className="rounded-lg border border-[var(--accent)]/30 bg-[var(--accent)]/5 p-4">
          <p className="text-sm text-[var(--foreground)]">
            <span className="font-medium">Email chưa được xác thực.</span> Chúng tôi đã gửi liên kết xác thực đến <span className="font-medium">{pendingEmail}</span>.
          </p>
          <p className="text-xs text-[var(--muted-foreground)] mt-1">
            Vui lòng kiểm tra hộp thư (và thư rác) để xác thực tài khoản.
          </p>
        </div>
      )}

      {/* Form */}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Email */}
        <div className="space-y-3">
          <Label htmlFor="email" className="text-sm font-medium">
            Email
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

        {/* Password */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <Label htmlFor="password" className="text-sm font-medium">
              Mật khẩu
            </Label>
            <Link
              href="/forgot-password"
              className="text-xs font-medium text-[var(--accent)] hover:underline"
            >
              Quên mật khẩu?
            </Link>
          </div>
          <div className="relative">
            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--muted-foreground)]" />
            <Input
              id="password"
              type={showPassword ? "text" : "password"}
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
              {...register("password")}
              className={`pl-11 pr-11 ${errors.password ? "border-[var(--destructive)] focus-visible:ring-[var(--destructive)]" : ""}`}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
            >
              {showPassword ? (
                <EyeOff className="h-4 w-4" />
              ) : (
                <Eye className="h-4 w-4" />
              )}
            </button>
          </div>
          {errors.password && (
            <p className="text-xs text-[var(--destructive)]">{errors.password.message}</p>
          )}
        </div>

        {/* Remember */}
        <div className="flex items-center gap-3">
          <Checkbox id="remember" {...register("remember")} />
          <Label htmlFor="remember" className="text-sm font-normal cursor-pointer">
            Ghi nhớ đăng nhập
          </Label>
        </div>

        {/* Submit */}
        <Button type="submit" className="w-full h-12 text-base font-medium bg-[var(--accent)] hover:bg-[var(--accent)]/90" disabled={isLoading}>
          {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Đăng nhập
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

      {/* Social Login */}
      <div className="grid grid-cols-1 gap-4">
        <Button
          variant="outline"
          className="h-12"
          onClick={handleGoogleLogin}
          disabled={isGoogleLoading || isLoading}
        >
          {isGoogleLoading ? (
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          ) : (
            <svg className="mr-2 h-5 w-5" viewBox="0 0 24 24">
              <path d="M22.6 12.25c0-.74-.07-1.45-.19-2.12H12v4.02h5.95a5.08 5.08 0 0 1-2.2 3.33v2.76h3.56c2.08-1.92 3.29-4.75 3.29-7.99Z" fill="#4285F4" />
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.56-2.76c-.98.66-2.23 1.05-3.72 1.05-2.86 0-5.29-1.93-6.16-4.53H2.18v2.85A11 11 0 0 0 12 23Z" fill="#34A853" />
              <path d="M5.84 14.1A6.6 6.6 0 0 1 5.5 12c0-.73.12-1.43.34-2.1V7.05H2.18A11 11 0 0 0 1 12c0 1.77.42 3.44 1.18 4.95l3.66-2.85Z" fill="#FBBC05" />
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.05L5.84 9.9C6.71 7.31 9.14 5.38 12 5.38Z" fill="#EA4335" />
            </svg>
          )}
          Đăng nhập với Google
        </Button>
      </div>

      {/* Terms */}
      <p className="text-center text-xs text-[var(--muted-foreground)]">
        Bằng cách đăng nhập, bạn đồng ý với{" "}
        <Link href="/terms" className="underline underline-offset-2 hover:text-[var(--foreground)]">
          Điều khoản dịch vụ
        </Link>{" "}
        và{" "}
        <Link href="/privacy" className="underline underline-offset-2 hover:text-[var(--foreground)]">
          Chính sách bảo mật
        </Link>{" "}
        của chúng tôi.
      </p>
    </div>
  )
}
