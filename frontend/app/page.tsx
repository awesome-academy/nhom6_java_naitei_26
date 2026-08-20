import Link from "next/link"
import { Button } from "@/components/ui/button"
import { SiteHeader } from "@/components/auth/site-header"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  ArrowRight,
  ArrowUpRight,
  Star,
  ChevronDown,
  Mail,
  Phone,
  MapPin,
  Search,
  Calendar,
  CreditCard,
  Shield,
  Users,
  Check,
  Bed,
  Clock,
  Sparkles,
  Heart,
  Home,
} from "lucide-react"

export default function HomePage() {
  return (
    <div className="min-h-screen bg-[var(--background)]">
      {/* Header */}
      <SiteHeader />

      <main>
        {/* Hero Section */}
        <section className="relative overflow-hidden bg-[var(--background)] py-20 sm:py-32 lg:py-40">
          {/* Background grid */}
          <div className="absolute inset-0 opacity-[0.03]">
            <svg className="h-full w-full" viewBox="0 0 100 100" preserveAspectRatio="none">
              <defs>
                <pattern id="hero-grid" width="4" height="4" patternUnits="userSpaceOnUse">
                  <path d="M 4 0 L 0 0 0 4" fill="none" stroke="black" strokeWidth="0.3"/>
                </pattern>
              </defs>
              <rect width="100" height="100" fill="url(#hero-grid)" />
            </svg>
          </div>

          <div className="relative mx-auto max-w-7xl px-6 lg:px-8">
            <div className="mx-auto max-w-4xl text-center">
              <h1 className="font-serif text-5xl font-medium leading-[1.05] tracking-tight text-[var(--foreground)] sm:text-6xl lg:text-7xl">
                Đặt phòng khách sạn
                <br />
                <em className="not-italic font-serif italic">dễ dàng & an toàn.</em>
              </h1>
              <p className="mx-auto mt-8 max-w-xl text-lg text-[var(--muted-foreground)]">
                Khám phá hàng ngàn khách sạn, homestay và resort trên toàn Việt Nam.
                Đặt phòng nhanh chóng, thanh toán linh hoạt, hỗ trợ tận tâm.
              </p>

              {/* CTA */}
              <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-3">
                <Button size="lg" asChild className="h-11 px-6 bg-[var(--primary)] text-white hover:bg-[var(--primary)]/90">
                  <Link href="/register">
                    Tìm phòng ngay
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </Link>
                </Button>
                <Button size="lg" variant="ghost" asChild className="h-11 px-6">
                  <Link href="#how-it-works">Xem cách đặt</Link>
                </Button>
              </div>

              {/* Trust Badge */}
              <div className="mt-12 flex items-center justify-center gap-6">
                <div className="flex -space-x-2">
                  {["NA", "TT", "LM", "PT", "HV"].map((initials, i) => (
                    <div
                      key={i}
                      className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-purple-600 text-white text-xs font-medium border-2 border-[var(--background)]"
                    >
                      {initials}
                    </div>
                  ))}
                </div>
                <div className="flex items-center gap-2">
                  <div className="flex">
                    {[1, 2, 3, 4].map((i) => (
                      <Star key={i} className="h-4 w-4 fill-yellow-400 text-yellow-400" />
                    ))}
                    <Star className="h-4 w-4 fill-yellow-400/50 text-yellow-400/50" />
                  </div>
                  <span className="text-sm font-medium text-[var(--foreground)]">4.8</span>
                  <span className="text-sm text-[var(--muted-foreground)]">từ 2,400+ đánh giá</span>
                </div>
              </div>
            </div>

            {/* Laptop Mockup - Hotel Booking Website */}
            <div className="relative mx-auto mt-20 max-w-5xl">
              {/* Laptop Frame */}
              <div className="relative mx-auto max-w-4xl">
                {/* Laptop top */}
                <div className="relative rounded-t-xl bg-gradient-to-b from-gray-800 to-gray-900 px-4 pt-4 pb-2">
                  {/* Camera */}
                  <div className="absolute left-1/2 top-2 -translate-x-1/2 h-2 w-2 rounded-full bg-gray-700"></div>
                  {/* Screen */}
                  <div className="overflow-hidden rounded-lg bg-white shadow-inner aspect-[16/10]">
                    {/* Browser bar */}
                    <div className="flex items-center gap-2 border-b border-gray-100 bg-gray-50 px-3 py-2">
                      <div className="flex gap-1.5">
                        <div className="h-3 w-3 rounded-full bg-red-400"></div>
                        <div className="h-3 w-3 rounded-full bg-yellow-400"></div>
                        <div className="h-3 w-3 rounded-full bg-green-400"></div>
                      </div>
                      <div className="flex-1 rounded bg-white px-3 py-1 text-xs text-gray-400">
                        tripstay.com
                      </div>
                    </div>

                    {/* Website Content */}
                    <div className="h-[calc(100%-37px)] bg-white flex">
                      {/* Hero image area */}
                      <div className="hidden lg:flex w-1/2 bg-gradient-to-br from-blue-500 via-purple-500 to-pink-500 relative p-4 flex-col justify-between">
                        <div className="text-white">
                          <p className="text-[9px] uppercase tracking-widest opacity-80">Khám phá</p>
                          <p className="font-serif text-base font-semibold mt-1">Điểm đến<br/>yêu thích</p>
                        </div>
                        <div className="space-y-1">
                          <div className="bg-white/20 backdrop-blur rounded p-1.5 text-[10px] text-white">Vũng Tàu</div>
                          <div className="bg-white/20 backdrop-blur rounded p-1.5 text-[10px] text-white">Đà Lạt</div>
                          <div className="bg-white/20 backdrop-blur rounded p-1.5 text-[10px] text-white">Phú Quốc</div>
                        </div>
                      </div>

                      <div className="flex-1 p-3 flex flex-col">
                        {/* Header */}
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center gap-3">
                            <div className="h-5 w-5 rounded bg-[var(--primary)] flex items-center justify-center text-white text-[10px] font-bold">T</div>
                            <div className="flex gap-2 text-[10px] text-gray-500">
                              <span>Khách sạn</span>
                              <span>Homestay</span>
                              <span>Resort</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-1">
                            <button className="rounded-full bg-[var(--primary)] px-2 py-0.5 text-[10px] text-white">Đăng nhập</button>
                          </div>
                        </div>

                        {/* Search Section */}
                        <div className="rounded-lg border border-gray-200 bg-white p-2 shadow-sm mb-3">
                          <div className="grid grid-cols-3 gap-2">
                            <div className="border-r border-gray-100 pr-1">
                              <p className="text-[8px] text-gray-400 uppercase tracking-wider">Địa điểm</p>
                              <p className="text-[11px] font-medium truncate">Vũng Tàu</p>
                            </div>
                            <div className="border-r border-gray-100 pr-1">
                              <p className="text-[8px] text-gray-400 uppercase tracking-wider">Nhận</p>
                              <p className="text-[11px] font-medium">22/08</p>
                            </div>
                            <div>
                              <p className="text-[8px] text-gray-400 uppercase tracking-wider">Trả</p>
                              <p className="text-[11px] font-medium">25/08</p>
                            </div>
                          </div>
                          <button className="mt-2 w-full rounded bg-[var(--accent)] py-1 text-[11px] text-white font-medium">
                            Tìm kiếm
                          </button>
                        </div>

                        {/* Featured Hotels */}
                        <div className="flex-1">
                          <div className="flex items-center justify-between mb-1.5">
                            <p className="font-serif text-[12px] font-semibold">Khách sạn nổi bật</p>
                            <span className="text-[10px] text-[var(--accent)]">Xem tất cả →</span>
                          </div>
                          <div className="grid grid-cols-3 gap-2">
                            <div className="rounded border border-gray-100 overflow-hidden">
                              <div className="h-12 bg-gradient-to-br from-blue-400 to-cyan-300 relative">
                                <span className="absolute top-0.5 left-0.5 bg-white/90 rounded px-1 py-px text-[8px] font-medium">★ 4.9</span>
                              </div>
                              <div className="p-1">
                                <p className="text-[10px] font-medium truncate">Grand Ocean</p>
                                <p className="text-[8px] text-gray-400">₫850K/đêm</p>
                              </div>
                            </div>
                            <div className="rounded border border-gray-100 overflow-hidden">
                              <div className="h-12 bg-gradient-to-br from-green-400 to-emerald-300 relative">
                                <span className="absolute top-0.5 left-0.5 bg-white/90 rounded px-1 py-px text-[8px] font-medium">★ 4.8</span>
                              </div>
                              <div className="p-1">
                                <p className="text-[10px] font-medium truncate">Mountain</p>
                                <p className="text-[8px] text-gray-400">₫620K/đêm</p>
                              </div>
                            </div>
                            <div className="rounded border border-gray-100 overflow-hidden">
                              <div className="h-12 bg-gradient-to-br from-purple-400 to-pink-300 relative">
                                <span className="absolute top-0.5 left-0.5 bg-white/90 rounded px-1 py-px text-[8px] font-medium">★ 4.7</span>
                              </div>
                              <div className="p-1">
                                <p className="text-[10px] font-medium truncate">Beach Para</p>
                                <p className="text-[8px] text-gray-400">₫1.2M/đêm</p>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                {/* Laptop bottom / keyboard */}
                <div className="h-5 bg-gradient-to-b from-gray-800 to-gray-900 rounded-b-xl">
                  <div className="h-full flex items-center justify-center">
                    <div className="h-1 w-32 rounded-full bg-gray-700"></div>
                  </div>
                </div>
                {/* Laptop base */}
                <div className="h-2 bg-gradient-to-b from-gray-300 to-gray-400 rounded-b-xl"></div>
              </div>
            </div>
          </div>
        </section>

        {/* Trusted By */}
        <section className="py-12 border-y border-[var(--border)] bg-[var(--card)]">
          <div className="mx-auto max-w-7xl px-6 lg:px-8">
            <p className="text-center text-sm font-mono uppercase tracking-widest text-[var(--muted-foreground)] mb-8">
              Được tin tưởng bởi các khách sạn hàng đầu
            </p>
            <div className="flex flex-wrap items-center justify-center gap-8 md:gap-16">
              {[
                { name: "Vinpearl", color: "text-blue-600" },
                { name: "Mövenpick", color: "text-amber-700" },
                { name: "InterContinental", color: "text-red-600" },
                { name: "Marriott", color: "text-gray-700" },
                { name: "Sheraton", color: "text-gray-800" },
              ].map((hotel, i) => (
                <span key={i} className={`font-serif text-xl font-medium ${hotel.color} opacity-60`}>
                  {hotel.name}
                </span>
              ))}
            </div>
          </div>
        </section>

        {/* How It Works */}
        <section id="how-it-works" className="py-20 lg:py-32">
          <div className="mx-auto max-w-7xl px-6 lg:px-8">
            <div className="mx-auto max-w-2xl text-center mb-16">
              <p className="font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]">
                Đơn giản
              </p>
              <h2 className="mt-4 font-serif text-4xl font-medium tracking-tight text-[var(--foreground)] sm:text-5xl">
                Đặt phòng chỉ trong 3 bước.
              </h2>
              <p className="mt-6 text-lg text-[var(--muted-foreground)]">
                Không cần đăng ký phức tạp. Tìm, chọn và đặt phòng chỉ trong vài phút.
              </p>
            </div>

            <div className="grid gap-8 md:grid-cols-3">
              {[
                {
                  step: "01",
                  title: "Tìm kiếm",
                  description: "Nhập địa điểm, ngày nhận và trả phòng. Hệ thống sẽ hiển thị tất cả phòng trống phù hợp.",
                },
                {
                  step: "02",
                  title: "Chọn phòng",
                  description: "So sánh giá, xem hình ảnh thực tế và đọc đánh giá từ khách đã ở. Chọn phòng ưng ý nhất.",
                },
                {
                  step: "03",
                  title: "Thanh toán",
                  description: "Điền thông tin, chọn phương thức thanh toán và nhận xác nhận tức thì qua email.",
                },
              ].map((item, i) => (
                <div key={i} className="text-center">
                  <div className="inline-flex items-center justify-center rounded-full border border-[var(--border)] bg-[var(--card)] px-4 py-2 mb-6">
                    <span className="font-serif text-4xl font-medium text-[var(--accent)]">{item.step}</span>
                  </div>
                  <h3 className="font-serif text-xl font-semibold text-[var(--foreground)]">{item.title}</h3>
                  <p className="mt-2 text-[var(--muted-foreground)]">{item.description}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Features */}
        <section className="py-20 lg:py-32 bg-[var(--card)]">
          <div className="mx-auto max-w-7xl px-6 lg:px-8">
            <div className="grid gap-16 lg:grid-cols-2 lg:gap-16 items-center">
              {/* Left - Text */}
              <div>
                <p className="font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]">
                  Tại sao chọn TripStay
                </p>
                <h2 className="mt-4 font-serif text-3xl font-medium tracking-tight text-[var(--foreground)] sm:text-4xl">
                  Đặt phòng thông minh,
                  <br />
                  <em className="not-italic font-serif italic">trải nghiệm tuyệt vời.</em>
                </h2>
                <p className="mt-6 text-base text-[var(--muted-foreground)]">
                  Chúng tôi kết nối bạn với hàng ngàn cơ sở lưu trú chất lượng, từ khách sạn 5 sao đến homestay xinh xắn.
                </p>

                <div className="mt-10 space-y-6">
                  {[
                    {
                      icon: Search,
                      title: "Hơn 10,000+ cơ sở lưu trú",
                      description: "Từ khách sạn sang trọng đến homestay bình dân, đáp ứng mọi nhu cầu và ngân sách.",
                    },
                    {
                      icon: Shield,
                      title: "Thanh toán bảo mật 100%",
                      description: "Mã hóa dữ liệu và nhiều phương thức thanh toán an toàn.",
                    },
                    {
                      icon: Clock,
                      title: "Hỗ trợ 24/7",
                      description: "Đội ngũ hỗ trợ luôn sẵn sàng giúp đỡ bạn mọi lúc, mọi nơi.",
                    },
                    {
                      icon: Star,
                      title: "Đánh giá thực tế",
                      description: "Hàng nghìn đánh giá từ khách đã lưu trú giúp bạn chọn đúng.",
                    },
                  ].map((item, i) => (
                    <div key={i} className="flex gap-4">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-[var(--border)] bg-[var(--background)]">
                        <item.icon className="h-5 w-5" />
                      </div>
                      <div>
                        <h3 className="text-base font-medium text-[var(--foreground)]">{item.title}</h3>
                        <p className="mt-1 text-sm text-[var(--muted-foreground)]">{item.description}</p>
                      </div>
                    </div>
                  ))}
                </div>

                <Button asChild className="mt-10 bg-[var(--primary)] text-white">
                  <Link href="/register">
                    Đăng ký miễn phí
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </Link>
                </Button>
              </div>

              {/* Right - Laptop with booking form */}
              <div className="flex justify-center lg:justify-end">
                <div className="relative w-full max-w-lg">
                  {/* Laptop Frame */}
                  <div className="relative rounded-t-xl bg-gradient-to-b from-gray-800 to-gray-900 px-4 pt-4 pb-2">
                    {/* Camera */}
                    <div className="absolute left-1/2 top-2 -translate-x-1/2 h-2 w-2 rounded-full bg-gray-700"></div>
                    {/* Screen */}
                    <div className="overflow-hidden rounded-lg bg-white shadow-inner aspect-[16/10]">
                      {/* Browser bar */}
                      <div className="flex items-center gap-2 border-b border-gray-100 bg-gray-50 px-3 py-2">
                        <div className="flex gap-1.5">
                          <div className="h-3 w-3 rounded-full bg-red-400"></div>
                          <div className="h-3 w-3 rounded-full bg-yellow-400"></div>
                          <div className="h-3 w-3 rounded-full bg-green-400"></div>
                        </div>
                        <div className="flex-1 rounded bg-white px-3 py-1 text-xs text-gray-400">
                          tripstay.com/booking
                        </div>
                      </div>

                      {/* Booking Form Content */}
                      <div className="h-[calc(100%-37px)] overflow-hidden p-3 flex flex-col">
                        <div className="mb-3">
                          <p className="font-serif text-sm font-semibold">Đặt phòng của bạn</p>
                          <p className="text-[10px] text-gray-400">Xác nhận tức thì qua email</p>
                        </div>

                        {/* Form fields */}
                        <div className="space-y-2 flex-1">
                          <div className="rounded border border-gray-200 p-2">
                            <p className="text-[8px] text-gray-400 uppercase tracking-wider">Khách sạn</p>
                            <p className="text-xs font-medium truncate">Grand Ocean View - Vũng Tàu</p>
                          </div>
                          <div className="grid grid-cols-2 gap-2">
                            <div className="rounded border border-gray-200 p-2">
                              <p className="text-[8px] text-gray-400 uppercase tracking-wider">Nhận phòng</p>
                              <p className="text-xs font-medium">22/08/2026</p>
                            </div>
                            <div className="rounded border border-gray-200 p-2">
                              <p className="text-[8px] text-gray-400 uppercase tracking-wider">Trả phòng</p>
                              <p className="text-xs font-medium">25/08/2026</p>
                            </div>
                          </div>
                          <div className="rounded border border-gray-200 p-2">
                            <p className="text-[8px] text-gray-400 uppercase tracking-wider">Loại phòng</p>
                            <p className="text-xs font-medium">Deluxe Ocean View</p>
                            <p className="text-[10px] text-gray-400">2 đêm · 2 người lớn</p>
                          </div>
                          <div className="rounded border border-gray-200 p-2 bg-gray-50">
                            <div className="flex justify-between text-[11px]">
                              <span>Giá phòng (2 đêm)</span>
                              <span className="font-medium">₫1,700,000</span>
                            </div>
                            <div className="flex justify-between text-[11px] mt-0.5">
                              <span>Phí dịch vụ</span>
                              <span className="font-medium">₫50,000</span>
                            </div>
                            <div className="flex justify-between font-semibold mt-1.5 pt-1.5 border-t border-gray-200">
                              <span className="text-xs">Tổng cộng</span>
                              <span className="text-xs text-[var(--accent)]">₫1,750,000</span>
                            </div>
                          </div>
                          <button className="w-full rounded bg-[var(--accent)] py-1.5 text-xs text-white font-semibold">
                            Xác nhận đặt phòng
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                  {/* Laptop bottom */}
                  <div className="h-5 bg-gradient-to-b from-gray-800 to-gray-900 rounded-b-xl">
                    <div className="h-full flex items-center justify-center">
                      <div className="h-1 w-32 rounded-full bg-gray-700"></div>
                    </div>
                  </div>
                  <div className="h-2 bg-gradient-to-b from-gray-300 to-gray-400 rounded-b-xl"></div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Stats */}
        <section className="py-20 lg:py-32">
          <div className="mx-auto max-w-7xl px-6 lg:px-8">
            <div className="grid gap-12 lg:grid-cols-2 lg:gap-16 items-center">
              {/* Left - Stats */}
              <div className="grid grid-cols-2 gap-8">
                {[
                  { value: "10,000+", label: "Cơ sở lưu trú", icon: Home },
                  { value: "50,000+", label: "Phòng trống", icon: Bed },
                  { value: "100,000+", label: "Đơn đặt thành công", icon: Calendar },
                  { value: "4.8", label: "Điểm đánh giá TB", icon: Star },
                ].map((stat, i) => (
                  <div key={i} className="text-left">
                    <stat.icon className="h-6 w-6 text-[var(--muted-foreground)] mb-4" />
                    <p className="font-serif text-5xl font-medium text-[var(--foreground)]">
                      {stat.value}
                    </p>
                    <p className="mt-1 text-base text-[var(--muted-foreground)]">
                      {stat.label}
                    </p>
                  </div>
                ))}
              </div>

              {/* Right - Testimonial */}
              <div>
                <div className="inline-flex items-center gap-2 rounded-full border border-[var(--border)] bg-[var(--card)] px-3 py-1 mb-6">
                  <Star className="h-3 w-3 fill-current" />
                  <span className="font-mono text-xs uppercase tracking-wider">Phản hồi khách hàng</span>
                </div>
                <blockquote className="font-serif text-2xl font-medium leading-snug text-[var(--foreground)] sm:text-3xl">
                  &ldquo;Đặt phòng qua TripStay cực kỳ dễ dàng. Giao diện đẹp, thanh toán nhanh và nhận xác nhận ngay lập tức. Tôi đã giới thiệu cho nhiều người bạn.&rdquo;
                </blockquote>
                <div className="mt-8 flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-purple-600 text-white text-sm font-medium">
                    NA
                  </div>
                  <div>
                    <p className="text-sm font-medium text-[var(--foreground)]">Nguyễn Anh</p>
                    <p className="text-sm text-[var(--muted-foreground)]">Khách hàng thường xuyên</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Pricing */}
        <section id="pricing" className="py-20 lg:py-32 bg-[var(--card)]">
          <div className="mx-auto max-w-7xl px-6 lg:px-8">
            <div className="mx-auto max-w-2xl text-center mb-16">
              <p className="font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]">
                Miễn phí cho người dùng
              </p>
              <h2 className="mt-4 font-serif text-4xl font-medium tracking-tight text-[var(--foreground)] sm:text-5xl">
                Đặt phòng không giới hạn.
              </h2>
              <p className="mt-6 text-lg text-[var(--muted-foreground)]">
                Không phí đặt phòng, không phí ẩn. Chỉ trả tiền cho những gì bạn sử dụng.
              </p>
            </div>

            <Card className="border-[var(--border)] overflow-hidden max-w-2xl mx-auto">
              <CardContent className="p-0">
                <div className="p-8 lg:p-10 text-center">
                  <h3 className="font-serif text-2xl font-medium">Người dùng TripStay</h3>
                  <p className="mt-2 text-[var(--muted-foreground)]">
                    Miễn phí vĩnh viễn cho mọi người
                  </p>
                  <div className="mt-6 flex items-baseline justify-center gap-1">
                    <span className="font-serif text-5xl font-medium">₫0</span>
                    <span className="text-sm text-[var(--muted-foreground)]">/vĩnh viễn</span>
                  </div>
                  <Button className="mt-6 w-full h-12 bg-[var(--primary)] text-white hover:bg-[var(--primary)]/90" asChild size="lg">
                    <Link href="/register">Bắt đầu đặt phòng miễn phí</Link>
                  </Button>
                </div>

                <div className="border-t border-[var(--border)] p-8 lg:p-10">
                  <div className="grid gap-4 sm:grid-cols-2">
                    {[
                      "Tìm kiếm hơn 10,000 cơ sở lưu trú",
                      "So sánh giá và đánh giá",
                      "Đặt phòng tức thì",
                      "Thanh toán an toàn",
                      "Xác nhận qua email/SMS",
                      "Hỗ trợ 24/7",
                      "Lưu lịch sử đặt phòng",
                      "Tích điểm thưởng",
                    ].map((feature, i) => (
                      <div key={i} className="flex items-start gap-3">
                        <div className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-green-100 text-green-600">
                          <Check className="h-3 w-3" />
                        </div>
                        <span className="text-sm text-[var(--foreground)]">{feature}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </section>

        {/* FAQ */}
        <section className="py-20 lg:py-32">
          <div className="mx-auto max-w-4xl px-6 lg:px-8">
            <div className="text-center mb-12">
              <p className="font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]">
                FAQ
              </p>
              <h2 className="mt-4 font-serif text-4xl font-medium tracking-tight text-[var(--foreground)] sm:text-5xl">
                Câu hỏi thường gặp.
              </h2>
            </div>

            <div className="divide-y divide-[var(--border)] border-t border-b border-[var(--border)]">
              {[
                {
                  question: "Làm sao để đặt phòng trên TripStay?",
                  answer: "Chỉ cần tìm kiếm theo địa điểm và ngày, chọn phòng ưng ý, điền thông tin và thanh toán. Bạn sẽ nhận xác nhận qua email ngay lập tức.",
                  defaultOpen: true,
                },
                {
                  question: "TripStay có thu phí đặt phòng không?",
                  answer: "Không. Người dùng đặt phòng hoàn toàn miễn phí. Chúng tôi kiếm tiền từ các đối tác khách sạn.",
                },
                {
                  question: "Tôi có thể hủy đặt phòng không?",
                  answer: "Có, tùy thuộc vào chính sách hủy của từng khách sạn. Thông tin hủy sẽ được hiển thị rõ ràng trước khi bạn xác nhận đặt phòng.",
                },
                {
                  question: "Phương thức thanh toán nào được chấp nhận?",
                  answer: "Chúng tôi chấp nhận thẻ tín dụng/ghi nợ (Visa, Mastercard), chuyển khoản ngân hàng và ví điện tử (VNPay, MoMo, ZaloPay).",
                },
                {
                  question: "Làm sao để liên hệ với khách sạn sau khi đặt?",
                  answer: "Thông tin liên hệ khách sạn sẽ được gửi trong email xác nhận. Bạn cũng có thể liên hệ qua mục hỗ trợ trong tài khoản TripStay.",
                },
                {
                  question: "Tôi có thể đặt phòng cho người khác không?",
                  answer: "Có, bạn có thể đặt phòng và nhập thông tin người lưu trú khác với thông tin người đặt trong bước thanh toán.",
                },
                {
                  question: "TripStay có ứng dụng di động không?",
                  answer: "Website TripStay hoạt động tốt trên mọi thiết bị, bao gồm điện thoại và máy tính bảng. Ứng dụng di động đang được phát triển và sẽ sớm ra mắt.",
                },
              ].map((faq, i) => (
                <details
                  key={i}
                  className="group py-6"
                  {...(faq.defaultOpen && { open: true })}
                >
                  <summary className="flex cursor-pointer items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-[var(--border)]">
                        <span className="text-sm font-medium">?</span>
                      </div>
                      <h3 className="font-serif text-lg font-medium text-[var(--foreground)]">
                        {faq.question}
                      </h3>
                    </div>
                    <ChevronDown className="h-5 w-5 text-[var(--muted-foreground)] transition-transform group-open:rotate-180" />
                  </summary>
                  <p className="mt-4 pl-10 text-[var(--muted-foreground)]">
                    {faq.answer}
                  </p>
                </details>
              ))}
            </div>
          </div>
        </section>

        {/* CTA + Newsletter */}
        <section className="bg-[#232323] text-white">
          <div className="mx-auto max-w-7xl px-6 lg:px-8 py-20">
            <div className="mx-auto max-w-3xl text-center">
              <h2 className="font-serif text-4xl font-medium leading-tight sm:text-5xl">
                Sẵn sàng cho chuyến đi tiếp theo?
              </h2>
              <p className="mt-4 font-serif text-2xl italic sm:text-3xl">
                Đăng ký và nhận ưu đãi độc quyền.
              </p>
              <div className="mt-8 flex flex-col sm:flex-row gap-3 max-w-md mx-auto">
                <div className="relative flex-1">
                  <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                  <input
                    type="email"
                    placeholder="email@company.com"
                    className="h-11 w-full rounded-md border border-white/20 bg-white pl-10 pr-4 text-sm text-[#232323] placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-white/40"
                  />
                </div>
                <Button className="h-11 bg-white text-[#232323] hover:bg-white/90">
                  Đăng ký
                </Button>
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="border-t border-white/10">
            <div className="mx-auto max-w-7xl px-6 lg:px-8 py-12">
              <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
                {[
                  { title: "SẢN PHẨM", links: ["Tìm khách sạn", "Bảng giá", "Ưu đãi", "Blog"] },
                  { title: "CÔNG TY", links: ["Giới thiệu", "Tuyển dụng", "Liên hệ", "Hỗ trợ"] },
                  { title: "TÀI NGUYÊN", links: ["Trung tâm trợ giúp", "Câu hỏi thường gặp", "Chính sách", "Điều khoản"] },
                  { title: "PHÁP LÝ", links: ["Bảo mật", "Điều khoản sử dụng", "Chính sách hoàn tiền", "GDPR"] },
                ].map((col, i) => (
                  <div key={i}>
                    <h4 className="font-mono text-xs uppercase tracking-widest text-white/60 mb-4">
                      {col.title}
                    </h4>
                    <ul className="space-y-3">
                      {col.links.map((link, j) => (
                        <li key={j}>
                          <Link href="#" className="text-base text-white/90 hover:text-white">
                            {link}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>

              {/* Contact Info */}
              <div className="mt-12 grid gap-6 sm:grid-cols-3 border-t border-white/10 pt-8">
                <div className="flex items-start gap-3">
                  <Phone className="h-5 w-5 mt-0.5 text-white/60" />
                  <div>
                    <p className="font-mono text-xs uppercase tracking-widest text-white/60">Liên hệ</p>
                    <p className="mt-1 text-base text-white">+84 (28) 555-0198</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <Mail className="h-5 w-5 mt-0.5 text-white/60" />
                  <div>
                    <p className="font-mono text-xs uppercase tracking-widest text-white/60">Email</p>
                    <p className="mt-1 text-base text-white">support@tripstay.com</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <MapPin className="h-5 w-5 mt-0.5 text-white/60" />
                  <div>
                    <p className="font-mono text-xs uppercase tracking-widest text-white/60">Văn phòng</p>
                    <p className="mt-1 text-base text-white">TP. Hồ Chí Minh, Việt Nam</p>
                  </div>
                </div>
              </div>

              {/* Bottom Bar */}
              <div className="mt-12 flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-white/10 pt-8">
                <div className="flex items-center gap-2">
                  <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-[#232323] font-bold text-sm">
                    T
                  </div>
                  <span className="text-base font-mono font-bold tracking-wider uppercase text-white">
                    TripStay
                  </span>
                </div>
                <p className="font-mono text-xs uppercase tracking-widest text-white/60">
                  © 2026 TRIPSTAY
                </p>
                <button className="flex items-center gap-2 text-sm text-white/90 hover:text-white">
                  <span>🌐</span>
                  Tiếng Việt
                  <ChevronDown className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
