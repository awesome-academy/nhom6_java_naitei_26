"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState, type ComponentType } from "react"
import {
  ArrowLeft,
  Building2,
  CheckCircle2,
  Clock3,
  CreditCard,
  Loader2,
  LockKeyhole,
  ReceiptText,
  Smartphone,
} from "lucide-react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { createBooking } from "@/lib/api/booking"
import { createPayment } from "@/lib/api/payment"
import {
  clearPaymentIdempotencyKey,
  getOrCreateIdempotencyKey,
  redirectToPaymentCheckout,
} from "@/lib/payment-checkout"
import type { Booking, BookingCreateRequest } from "@/types/booking"
import type { PaymentMethod } from "@/types/payment"

type PaymentMethodOption = {
  value: PaymentMethod
  label: string
  description: string
  icon: ComponentType<{ className?: string }>
}

const paymentMethods: PaymentMethodOption[] = [
  {
    value: "E_WALLET",
    label: "Ví điện tử (Mock)",
    description: "Mở simulator QR để thử nghiệm thanh toán ngay trong local.",
    icon: Smartphone,
  },
  {
    value: "INTERNET_BANKING",
    label: "Internet Banking",
    description: "Cổng SePay sandbox, cần merchant và callback URL hợp lệ.",
    icon: Building2,
  },
  {
    value: "CARD",
    label: "Thẻ",
    description: "Cổng SePay sandbox, cần cấu hình gateway trước khi dùng.",
    icon: CreditCard,
  },
]

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(value)
}

function formatDateTime(value: string | null) {
  if (!value) return "Theo xác nhận của hệ thống"
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value))
}

function getErrorMessage(error: unknown) {
  if (!(error instanceof Error)) return "Không thể khởi tạo thanh toán."
  return error.message || "Không thể khởi tạo thanh toán."
}

type PaymentDraftSummary = {
  totalAmount: number
  currency: string
}

export function BookingPaymentStep({
  booking: initialBooking,
  draft,
  summary,
}: {
  booking?: Booking
  draft?: BookingCreateRequest
  summary?: PaymentDraftSummary
}) {
  const router = useRouter()
  const [booking, setBooking] = useState<Booking | null>(initialBooking ?? null)
  const [method, setMethod] = useState<PaymentMethod>("E_WALLET")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function startPayment() {
    setIsSubmitting(true)
    setError(null)

    try {
      let currentBooking = booking
      if (!currentBooking) {
        if (!draft) throw new Error("Thông tin booking tạm thời không còn. Vui lòng chọn phòng lại.")
        currentBooking = await createBooking(draft)
        setBooking(currentBooking)
        toast.success("Đã giữ phòng tạm thời trong 15 phút")
      }

      if (!currentBooking) throw new Error("Không thể xác định booking để thanh toán.")

      const idempotencyKey = getOrCreateIdempotencyKey(currentBooking.publicId, method)
      const payment = await createPayment(currentBooking.publicId, method, idempotencyKey)

      if (payment.status === "SUCCEEDED") {
        router.replace(`/profile/bookings/${currentBooking.publicId}`)
        return
      }
      if (payment.status !== "PENDING" && payment.status !== "PROCESSING") {
        clearPaymentIdempotencyKey(currentBooking.publicId, method)
        throw new Error("Lần thanh toán trước đã kết thúc. Vui lòng thử lại.")
      }

      toast.success("Đã tạo yêu cầu thanh toán")
      redirectToPaymentCheckout(currentBooking, payment)
    } catch (paymentError) {
      const message = getErrorMessage(paymentError)
      setError(message)
      toast.error(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="min-h-[calc(100vh-4rem)] bg-[#f7f8fb]">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-8 px-6 py-10 lg:px-8 lg:py-14">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-3xl">
            <Badge variant="outline" className="mb-4 w-fit border-blue-200 bg-blue-50 text-blue-700">
              Thanh toán an toàn
            </Badge>
            <h1 className="font-serif text-4xl font-medium tracking-tight text-[var(--foreground)] sm:text-[2.75rem]">
              Hoàn tất thanh toán cho booking
            </h1>
            <p className="mt-3 max-w-2xl text-base leading-7 text-[var(--muted-foreground)]">
              Phòng của bạn đang được giữ tạm thời. Thanh toán toàn bộ booking để xác nhận trước khi thời hạn giữ phòng kết thúc.
            </p>
          </div>
          <Button asChild variant="ghost" className="w-fit">
            <Link href={booking ? `/profile/bookings/${booking.publicId}` : "/profile/bookings"}>
              <ArrowLeft data-icon="inline-start" />
              {booking ? "Quay lại booking" : "Quay lại danh sách booking"}
            </Link>
          </Button>
        </div>

        {error && (
          <Alert variant="destructive">
            <ReceiptText />
            <AlertTitle>Không thể tiếp tục thanh toán</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
          <section className="rounded-2xl border border-[#e3e7ef] bg-white p-6 shadow-[0_10px_30px_rgba(30,41,59,0.06)] sm:p-8">
            <div className="mb-7 flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-blue-700">Phương thức</p>
                <h2 className="mt-2 font-serif text-2xl font-medium text-[#202532]">Chọn cách thanh toán</h2>
              </div>
              <LockKeyhole className="size-5 text-blue-600" />
            </div>
            <ToggleGroup
              type="single"
              value={method}
              onValueChange={(value) => value && setMethod(value as PaymentMethod)}
              variant="outline"
              aria-label="Phương thức thanh toán"
              className="grid w-full gap-3 md:grid-cols-3"
            >
              {paymentMethods.map((option) => {
                const Icon = option.icon
                return (
                  <ToggleGroupItem
                    key={option.value}
                    value={option.value}
                    aria-label={option.label}
                    className="group h-auto min-h-36 w-full flex-col items-start justify-start whitespace-normal rounded-xl border border-[#e3e7ef] bg-white px-4 py-4 text-left text-[#202532] shadow-none transition hover:border-blue-300 hover:bg-[#fafcff] data-[state=on]:!border-blue-600 data-[state=on]:!bg-[#eef3ff] data-[state=on]:!text-[#202532] data-[state=on]:shadow-[0_0_0_3px_rgba(37,99,235,0.10)]"
                  >
                    <Icon className="mb-3 text-[#687386] group-data-[state=on]:text-blue-700" />
                    <span className="font-semibold">{option.label}</span>
                    <span className="mt-1 text-xs font-normal leading-5 text-[#687386] group-data-[state=on]:text-[#4b5870]">
                      {option.description}
                    </span>
                  </ToggleGroupItem>
                )
              })}
            </ToggleGroup>
            <div className="mt-8 flex flex-col gap-4 border-t border-[#e8ebf1] pt-6 sm:flex-row sm:items-center sm:justify-between">
              <p className="max-w-sm text-sm leading-6 text-muted-foreground">
                Kết quả thanh toán chỉ được xác nhận từ backend và gateway.
              </p>
              <Button onClick={startPayment} disabled={isSubmitting} size="lg" className="shrink-0 bg-blue-600 text-white hover:bg-blue-700">
                {isSubmitting ? (
                  <Loader2 data-icon="inline-start" className="animate-spin" />
                ) : (
                  <CreditCard data-icon="inline-start" />
                )}
                {isSubmitting ? "Đang khởi tạo..." : "Tiếp tục thanh toán"}
              </Button>
            </div>
          </section>

          <aside className="h-fit overflow-hidden rounded-2xl border border-[#e3e7ef] bg-white shadow-[0_10px_30px_rgba(30,41,59,0.06)]">
            <div className="border-b border-[#e8ebf1] bg-[#f8faff] p-6 sm:p-7">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[#687386]">Tóm tắt đơn</p>
                <p className="mt-3 font-mono text-sm font-medium text-[#202532]">{booking?.bookingCode ?? "Bản nháp booking"}</p>
                </div>
                <CheckCircle2 className="size-5 text-blue-600" />
              </div>
              <div className="mt-7 flex flex-col gap-4">
                <SummaryRow label="Tổng booking" value={formatMoney(booking?.totalAmount ?? summary?.totalAmount ?? 0, booking?.currency ?? summary?.currency ?? "VND")} />
                <div className="rounded-xl border border-blue-200 bg-white p-4">
                  <SummaryRow label="Thanh toán đầy đủ" value={formatMoney(booking?.totalAmount ?? summary?.totalAmount ?? 0, booking?.currency ?? summary?.currency ?? "VND")} emphasized />
                </div>
              </div>
            </div>
            <div className="flex items-start gap-3 p-6 sm:p-7">
              <Clock3 className="mt-0.5 size-5 shrink-0 text-blue-600" />
              <div className="flex flex-col gap-1">
                <span className="text-sm font-semibold text-[#202532]">Thời hạn giữ phòng</span>
                <span className="text-sm leading-6 text-[#687386]">{formatDateTime(booking?.holdExpiresAt ?? null)}</span>
                <span className="mt-2 text-xs leading-5 text-[#687386]">
                  Booking được xác nhận sau khi backend xác thực đủ số tiền thanh toán.
                </span>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </main>
  )
}

function SummaryRow({
  label,
  value,
  emphasized = false,
}: {
  label: string
  value: string
  emphasized?: boolean
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={emphasized ? "text-lg font-semibold" : "text-sm font-medium"}>
        {value}
      </span>
    </div>
  )
}
