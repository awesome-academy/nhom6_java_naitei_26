"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { zodResolver } from "@hookform/resolvers/zod"
import { Eye, EyeOff, Loader2, Lock, Mail, ShieldCheck } from "lucide-react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { login } from "@/lib/api/auth"
import { isBackOfficeUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"
import type { LoginFormData } from "@/types/auth"

const adminLoginSchema = z.object({
  email: z.string().trim().min(1, "Email là bắt buộc").email("Email không hợp lệ"),
  password: z.string().min(1, "Mật khẩu là bắt buộc"),
})

type AdminLoginFormValues = z.infer<typeof adminLoginSchema>

function getSafeRedirect(value: string | null): string {
  if (!value || (value !== "/manager" && !value.startsWith("/manager/")) || value.startsWith("/manager/login")) {
    return "/manager"
  }
  return value
}

function getErrorMessage(error: unknown): string {
  const apiError = error as { status?: number; message?: string }
  if (apiError.status === 401) return "Email hoặc mật khẩu không đúng"
  if (apiError.status === 423) return "Tài khoản đang bị khóa tạm thời. Vui lòng thử lại sau."
  if (apiError.status === 403) return "Tài khoản chưa sẵn sàng để đăng nhập."
  return apiError.message || "Không thể đăng nhập Manager. Vui lòng thử lại."
}

export function AdminLoginForm() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { user, isAuthenticated, isLoading: isAuthLoading, setAuth, clearAuth } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isRedirecting, setIsRedirecting] = useState(false)

  const requestedRedirect = useMemo(() => searchParams.get("redirect"), [searchParams])
  const wasForbidden = searchParams.get("reason") === "forbidden"

  const form = useForm<AdminLoginFormValues>({
    resolver: zodResolver(adminLoginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  })

  useEffect(() => {
    if (!isAuthLoading && isAuthenticated && isBackOfficeUser(user)) {
      router.replace(getSafeRedirect(requestedRedirect))
    }
  }, [isAuthLoading, isAuthenticated, requestedRedirect, router, user])

  async function submit(values: AdminLoginFormValues) {
    setIsSubmitting(true)
    setError(null)

    try {
      const response = await login({
        email: values.email.trim(),
        password: values.password,
      } satisfies LoginFormData)

      if (!isBackOfficeUser(response.user)) {
        clearAuth()
        setError("Tài khoản này không có role STAFF hoặc ADMIN. Vui lòng dùng tài khoản Manager.")
        return
      }

      setAuth(response.user, response.accessToken, response.refreshToken)
      setIsRedirecting(true)
      toast.success("Đăng nhập Manager thành công")
    } catch (loginError) {
      setError(getErrorMessage(loginError))
      setIsRedirecting(false)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader className="gap-3 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <ShieldCheck />
          </div>
          <div>
          <CardTitle className="text-2xl">Đăng nhập Manager</CardTitle>
            <CardDescription>
            Chỉ tài khoản đã được gán role STAFF hoặc ADMIN trong database mới truy cập được khu Manager.
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-5">
            {(error || wasForbidden) && (
              <Alert variant="destructive">
                <AlertDescription>
                  {error ?? "Phiên hiện tại không có quyền STAFF hoặc ADMIN. Vui lòng đăng nhập tài khoản Manager."}
                </AlertDescription>
              </Alert>
            )}

            <Form {...form}>
              <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-5">
                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Email Manager</FormLabel>
                      <FormControl>
                        <div className="relative">
                          <Mail className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                          <Input
                            type="email"
                            autoComplete="email"
                            placeholder="manager@tripstay.com"
                            className="pl-10"
                            {...field}
                          />
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Mật khẩu</FormLabel>
                      <FormControl>
                        <div className="relative">
                          <Lock className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                          <Input
                            type={showPassword ? "text" : "password"}
                            autoComplete="current-password"
                            placeholder="Nhập mật khẩu Manager"
                            className="pl-10 pr-10"
                            {...field}
                          />
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="absolute right-0 top-0"
                            onClick={() => setShowPassword((current) => !current)}
                            aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                          >
                            {showPassword ? <EyeOff /> : <Eye />}
                          </Button>
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <Button type="submit" className="w-full" disabled={isSubmitting || isRedirecting}>
                  {(isSubmitting || isRedirecting) && <Loader2 data-icon="inline-start" className="animate-spin" />}
                  {isRedirecting ? "Đang vào khu Manager..." : "Đăng nhập Manager"}
                </Button>
              </form>
            </Form>

            <p className="text-center text-sm text-muted-foreground">
              Quay lại{" "}
              <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
                đăng nhập khách hàng
              </Link>
            </p>
          </div>
        </CardContent>
      </Card>
    </main>
  )
}
