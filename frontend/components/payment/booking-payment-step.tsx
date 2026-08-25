"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState, type ComponentType } from "react"
import {
  Building2,
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
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { createPayment } from "@/lib/api/payment"
import {
  clearPaymentIdempotencyKey,
  getOrCreateIdempotencyKey,
  redirectToPaymentCheckout,
} from "@/lib/payment-checkout"
import type { Booking } from "@/types/booking"
import type { PaymentMethod } from "@/types/payment"

type PaymentMethodOption = {
  value: PaymentMethod
  label: string
  description: string
  icon: ComponentType<{ className?: string }>
}

const paymentMethods: PaymentMethodOption[] = [
  {
    value: "INTERNET_BANKING",
    label: "Internet Banking",
    description: "Chuyển tới cổng SePay để thanh toán qua ngân hàng.",
    icon: Building2,
  },
  {
    value: "CARD",
    label: "Thẻ",
    description: "Thanh toán bằng thẻ qua cổng SePay bảo mật.",
    icon: CreditCard,
  },
  {
    value: "E_WALLET",
    label: "Ví điện tử",
    description: "Quét QR và thử luồng thanh toán mock trong môi trường dev.",
    icon: Smartphone,
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

export function BookingPaymentStep({ booking }: { booking: Booking }) {
  const router = useRouter()
  const [method, setMethod] = useState<PaymentMethod>("INTERNET_BANKING")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function startPayment() {
    setIsSubmitting(true)
    setError(null)

    try {
      const idempotencyKey = getOrCreateIdempotencyKey(booking.publicId, method)
      const payment = await createPayment(booking.publicId, method, idempotencyKey)

      if (payment.status === "SUCCEEDED") {
        router.replace(`/profile/bookings/${booking.publicId}`)
        return
      }
      if (payment.status !== "PENDING" && payment.status !== "PROCESSING") {
        clearPaymentIdempotencyKey(booking.publicId, method)
        throw new Error("Lần thanh toán trước đã kết thúc. Vui lòng thử lại.")
      }

      toast.success("Đã tạo yêu cầu thanh toán")
      redirectToPaymentCheckout(booking, payment)
    } catch (paymentError) {
      const message = getErrorMessage(paymentError)
      setError(message)
      toast.error(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-8 lg:px-8">
      <PaymentProgress />

      <div className="flex flex-col gap-2">
        <Badge variant="outline" className="w-fit">Bước thanh toán</Badge>
        <h1 className="font-serif text-3xl tracking-tight md:text-4xl">
          Hoàn tất tiền cọc cho booking
        </h1>
        <p className="text-muted-foreground">
          Booking đã được giữ tạm thời. Chọn phương thức để thanh toán tiền cọc.
        </p>
      </div>

      {error && (
        <Alert variant="destructive">
          <ReceiptText />
          <AlertTitle>Không thể tiếp tục thanh toán</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <Card>
          <CardHeader>
            <CardTitle>Chọn phương thức thanh toán</CardTitle>
            <CardDescription>
              Internet Banking và Thẻ chuyển tới SePay; Ví điện tử dùng mock gateway ở môi trường dev.
            </CardDescription>
          </CardHeader>
          <CardContent>
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
                    className="h-auto min-h-28 w-full flex-col items-start justify-start whitespace-normal px-4 py-4 text-left"
                  >
                    <Icon />
                    <span className="font-semibold">{option.label}</span>
                    <span className="text-xs font-normal text-muted-foreground">
                      {option.description}
                    </span>
                  </ToggleGroupItem>
                )
              })}
            </ToggleGroup>
          </CardContent>
          <CardFooter className="flex-col items-stretch gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <LockKeyhole />
              Kết quả chỉ được xác nhận từ backend/gateway.
            </div>
            <Button onClick={startPayment} disabled={isSubmitting} size="lg">
              {isSubmitting ? (
                <Loader2 data-icon="inline-start" className="animate-spin" />
              ) : (
                <CreditCard data-icon="inline-start" />
              )}
              {isSubmitting ? "Đang khởi tạo..." : "Tiếp tục thanh toán"}
            </Button>
          </CardFooter>
        </Card>

        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Thông tin đơn</CardTitle>
            <CardDescription>{booking.bookingCode}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <SummaryRow label="Tổng booking" value={formatMoney(booking.totalAmount, booking.currency)} />
            <SummaryRow
              label={`Tiền cọc (${booking.depositPercentSnapshot}%)`}
              value={formatMoney(booking.requiredDepositAmount, booking.currency)}
              emphasized
            />
            <Separator />
            <div className="flex items-start gap-3">
              <Clock3 className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium">Thời hạn giữ phòng</span>
                <span className="text-sm text-muted-foreground">
                  {formatDateTime(booking.holdExpiresAt)}
                </span>
              </div>
            </div>
          </CardContent>
          <CardFooter>
            <Button asChild variant="outline" className="w-full">
              <Link href={`/profile/bookings/${booking.publicId}`}>Xem chi tiết booking</Link>
            </Button>
          </CardFooter>
        </Card>
      </div>
    </main>
  )
}

function PaymentProgress() {
  const steps = ["Chọn phòng", "Thông tin khách", "Xác nhận", "Thanh toán"]

  return (
    <ol className="grid grid-cols-2 gap-3 md:grid-cols-4" aria-label="Tiến trình đặt phòng">
      {steps.map((step, index) => (
        <li key={step} className="flex items-center gap-2">
          <Badge variant={index === steps.length - 1 ? "default" : "success"}>{index + 1}</Badge>
          <span className="text-sm font-medium">{step}</span>
        </li>
      ))}
    </ol>
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
