"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Check } from "lucide-react"

interface AuthLayoutNewProps {
  children: React.ReactNode
}

export function AuthLayoutNew({ children }: AuthLayoutNewProps) {
  const pathname = usePathname()
  const isLogin = pathname.includes("/login")

  return (
    <div className="flex min-h-screen">
      {/* Left Panel - Branding */}
      <div className="hidden lg:flex lg:w-1/2 bg-[#232323] flex-col justify-between p-16 relative overflow-hidden">
        {/* Background Pattern */}
        <div className="absolute inset-0 opacity-[0.03]">
          <svg className="w-full h-full" viewBox="0 0 100 100" preserveAspectRatio="none">
            <defs>
              <pattern id="grid" width="10" height="10" patternUnits="userSpaceOnUse">
                <path d="M 10 0 L 0 0 0 10" fill="none" stroke="white" strokeWidth="0.5"/>
              </pattern>
            </defs>
            <rect width="100" height="100" fill="url(#grid)" />
          </svg>
        </div>

        {/* Decorative Elements */}
        <div className="absolute top-1/4 -right-32 w-64 h-64 bg-[#2563eb]/10 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 -left-32 w-48 h-48 bg-[#2563eb]/5 rounded-full blur-2xl" />

        {/* Logo */}
        <div className="relative z-10">
          <Link href="/" className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-[#2563eb] flex items-center justify-center">
              <span className="text-white font-bold text-lg">T</span>
            </div>
            <span className="text-2xl font-mono font-bold text-white tracking-wider uppercase">
              TripStay
            </span>
          </Link>
        </div>

        {/* Main Content */}
        <div className="relative z-10 space-y-10">
          <div className="space-y-6">
            <h1 className="font-serif text-5xl font-medium text-white leading-tight tracking-tight">
              {isLogin
                ? "Chào mừng bạn quay trở lại"
                : "Tạo tài khoản mới"
              }
            </h1>
            <p className="text-lg text-gray-400 max-w-md leading-relaxed">
              {isLogin
                ? "Đăng nhập để tiếp tục đặt phòng và quản lý booking của bạn một cách dễ dàng."
                : "Đăng ký để trải nghiệm đặt phòng khách sạn dễ dàng với nhiều ưu đãi hấp dẫn."
              }
            </p>
          </div>

          {/* Features List */}
          <div className="space-y-5">
            {[
              "Đặt phòng 24/7 với xác thực bảo mật",
              "Quản lý booking thông minh",
              "Hỗ trợ khách hàng tận tâm",
            ].map((feature, i) => (
              <div key={i} className="flex items-center gap-4 text-gray-300">
                <div className="w-6 h-6 rounded-full bg-[#2563eb]/20 flex items-center justify-center">
                  <Check className="w-3.5 h-3.5 text-[#2563eb]" />
                </div>
                <span className="text-base">{feature}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Testimonial */}
        <div className="relative z-10 p-8 rounded-2xl bg-white/[0.03] backdrop-blur-sm border border-white/[0.08]">
          <div className="flex items-center gap-1 mb-4">
            {[...Array(5)].map((_, i) => (
              <svg key={i} className="w-4 h-4 text-yellow-400 fill-current" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
            ))}
          </div>
          <p className="text-gray-300 italic leading-relaxed mb-6">
            &ldquo;TripStay giúp tôi đặt phòng nhanh chóng và tiện lợi. Giao diện rất dễ sử dụng!&rdquo;
          </p>
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-semibold text-lg">
              NA
            </div>
            <div>
              <p className="text-white font-medium">Nguyễn Minh Anh</p>
              <p className="text-gray-400 text-sm">Khách hàng thường xuyên</p>
            </div>
          </div>
        </div>
      </div>

      {/* Right Panel - Auth Form */}
      <div className="flex-1 flex items-center justify-center p-8 bg-[var(--background)]">
        <div className="w-full max-w-md">
          {children}
        </div>
      </div>
    </div>
  )
}
