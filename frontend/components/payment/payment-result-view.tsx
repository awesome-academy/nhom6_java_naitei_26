"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useState, type ComponentType } from "react"
import {
  Ban,
  CheckCircle2,
  Clock3,
  Loader2,
  ReceiptText,
  RotateCcw,
  XCircle,
} from "lucide-react"

import { SiteHeader } from "@/components/auth/site-header"
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
import { cancelPayment, getPayment } from "@/lib/api/payment"
import {
  clearPaymentCheckout,
  clearPaymentIdempotencyKey,
  loadPaymentCheckout,
  type PaymentCheckoutSession,
} from "@/lib/payment-checkout"
import type { PaymentStatusResponse } from "@/types/payment"

export type PaymentReturnOutcome = "success" | "error" | "cancel"
type ResultState = "loading" | "success" | "failed" | "cancelled" | "pending" | "missing"

const terminalStates: ResultState[] = ["success", "failed", "cancelled"]

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(value)
}

function getErrorMessage(error: unknown) {
  if (!(error instanceof Error)) return "Không thể kiểm tra trạng thái payment."
  return error.message || "Không thể kiểm tra trạng thái payment."
}

function mapPaymentState(payment: PaymentStatusResponse): ResultState | null {
  if (["SUCCEEDED", "REFUNDED", "PARTIALLY_REFUNDED"].includes(payment.status)) {
    return "success"
  }
  if (["FAILED", "EXPIRED"].includes(payment.status)) return "failed"
  if (payment.status === "CANCELLED") return "cancelled"
  return null
}

function wait(duration: number) {
  return new Promise((resolve) => window.setTimeout(resolve, duration))
}

export function PaymentResultView({
  outcome,
  bookingPublicId,
  paymentCode,
}: {
  outcome: PaymentReturnOutcome
  bookingPublicId?: string
  paymentCode?: string
}) {
  const router = useRouter()
  const [result, setResult] = useState<ResultState>("loading")
  const [checkout, setCheckout] = useState<PaymentCheckoutSession | null>(null)
  const [payment, setPayment] = useState<PaymentStatusResponse | null>(null)
  const [resolvedBookingId, setResolvedBookingId] = useState<string | null>(null)
  const [resolvedPaymentCode, setResolvedPaymentCode] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [redirectSeconds, setRedirectSeconds] = useState(3)

  useEffect(() => {
    let ignore = false

    async function resolvePaymentResult() {
      const storedCheckout = loadPaymentCheckout()
      const targetBookingId = bookingPublicId || storedCheckout?.bookingPublicId
      const targetPaymentCode = paymentCode || storedCheckout?.paymentCode

      if (!targetBookingId || !targetPaymentCode) {
        if (!ignore) setResult("missing")
        return
      }
      if (
        storedCheckout &&
        (storedCheckout.bookingPublicId !== targetBookingId ||
          storedCheckout.paymentCode !== targetPaymentCode)
      ) {
        if (!ignore) setResult("missing")
        return
      }

      if (!ignore) {
        setCheckout(storedCheckout)
        setResolvedBookingId(targetBookingId)
        setResolvedPaymentCode(targetPaymentCode)
      }

      try {
        let currentPayment = await getPayment(targetBookingId, targetPaymentCode)

        if (
          outcome === "cancel" &&
          ["PENDING", "PROCESSING"].includes(currentPayment.status)
        ) {
          currentPayment = await cancelPayment(targetBookingId, targetPaymentCode)
        }

        if (outcome === "success") {
          for (let attempt = 0; attempt < 20; attempt += 1) {
            const mappedState = mapPaymentState(currentPayment)
            if (mappedState || ignore) break
            await wait(1500)
            if (ignore) return
            currentPayment = await getPayment(targetBookingId, targetPaymentCode)
          }
        }

        if (ignore) return
        setPayment(currentPayment)

        const verifiedState = mapPaymentState(currentPayment)
        if (verifiedState) {
          if (verifiedState === "failed") {
            setMessage(currentPayment.failureMessage)
          }
          setResult(verifiedState)
          return
        }

        if (outcome === "error") {
          setMessage(currentPayment.failureMessage || "Cổng thanh toán báo giao dịch không thành công.")
          setResult("failed")
          return
        }

        setResult("pending")
      } catch (statusError) {
        if (!ignore) {
          setMessage(getErrorMessage(statusError))
          setResult(outcome === "success" ? "pending" : "failed")
        }
      }
    }

    resolvePaymentResult()
    return () => {
      ignore = true
    }
  }, [bookingPublicId, outcome, paymentCode])

  useEffect(() => {
    if (!terminalStates.includes(result) || !resolvedBookingId) return

    const countdownTimer = window.setInterval(() => {
      setRedirectSeconds((current) => Math.max(0, current - 1))
    }, 1000)
    const redirectTimer = window.setTimeout(() => {
      if (checkout) {
        clearPaymentIdempotencyKey(checkout.bookingPublicId, checkout.method)
      }
      clearPaymentCheckout()
      router.replace(`/profile/bookings/${resolvedBookingId}`)
    }, 3000)

    return () => {
      window.clearInterval(countdownTimer)
      window.clearTimeout(redirectTimer)
    }
  }, [checkout, resolvedBookingId, result, router])

  const presentation = getResultPresentation(result)
  const ResultIcon = presentation.icon

  return (
    <div className="min-h-screen bg-muted/30">
      <SiteHeader />
      <main className="mx-auto flex min-h-[75vh] w-full max-w-2xl items-center px-6 py-8">
        <Card className="w-full">
          <CardHeader className="items-center text-center">
            <div className="flex size-16 items-center justify-center rounded-full bg-muted">
              <ResultIcon className={result === "failed" ? "text-destructive" : "text-primary"} />
            </div>
            <Badge variant={presentation.badgeVariant}>{presentation.badge}</Badge>
            <CardTitle className="text-2xl">{presentation.title}</CardTitle>
            <CardDescription>{presentation.description}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {result === "loading" && (
              <Alert>
                <Loader2 className="animate-spin" />
                <AlertTitle>Đang xác minh giao dịch</AlertTitle>
                <AlertDescription>
                  Frontend đang chờ trạng thái đã được backend xác nhận từ gateway.
                </AlertDescription>
              </Alert>
            )}

            {result === "pending" && (
              <Alert>
                <Clock3 />
                <AlertTitle>Gateway chưa xác nhận xong</AlertTitle>
                <AlertDescription>
                  {message || "Giao dịch vẫn đang xử lý. Bạn có thể kiểm tra lại trong Booking Detail."}
                </AlertDescription>
              </Alert>
            )}

            {message && result === "failed" && (
              <Alert variant="destructive">
                <ReceiptText />
                <AlertTitle>Lý do</AlertTitle>
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            )}

            {(checkout || payment) && (
              <div className="grid gap-3 rounded-lg bg-muted p-4 text-sm sm:grid-cols-2">
                <SummaryItem label="Mã booking" value={checkout?.bookingCode ?? resolvedBookingId ?? "—"} />
                <SummaryItem label="Mã payment" value={payment?.paymentCode ?? resolvedPaymentCode ?? "—"} />
                <SummaryItem
                  label="Số tiền"
                  value={formatMoney(
                    Number(payment?.amount ?? checkout?.amount ?? 0),
                    payment?.currency ?? checkout?.currency ?? "VND"
                  )}
                />
                <SummaryItem label="Trạng thái backend" value={payment?.status ?? "Đang kiểm tra"} />
              </div>
            )}

            {terminalStates.includes(result) && (
              <p className="text-center text-sm text-muted-foreground">
                Tự động chuyển về Booking Detail sau {redirectSeconds} giây.
              </p>
            )}
          </CardContent>
          <CardFooter className="flex-col gap-3 sm:flex-row sm:justify-center">
            {resolvedBookingId && (
              <Button asChild>
                <Link href={`/profile/bookings/${resolvedBookingId}`}>Xem Booking Detail</Link>
              </Button>
            )}
            {(result === "failed" || result === "cancelled") && (
              <Button asChild variant="outline">
                <Link href="/profile/bookings">
                  <RotateCcw data-icon="inline-start" />
                  Quay lại danh sách booking
                </Link>
              </Button>
            )}
            {result === "missing" && (
              <Button asChild variant="outline">
                <Link href="/profile/bookings">Danh sách booking</Link>
              </Button>
            )}
          </CardFooter>
        </Card>
      </main>
    </div>
  )
}

function getResultPresentation(state: ResultState): {
  icon: ComponentType<{ className?: string }>
  badge: string
  badgeVariant: "outline" | "success" | "destructive" | "warning"
  title: string
  description: string
} {
  if (state === "success") {
    return {
      icon: CheckCircle2,
      badge: "Thanh toán thành công",
      badgeVariant: "success",
      title: "Booking đã được xác nhận",
      description: "Gateway đã xác minh giao dịch và backend đã ghi nhận khoản thanh toán.",
    }
  }
  if (state === "failed") {
    return {
      icon: XCircle,
      badge: "Thanh toán thất bại",
      badgeVariant: "destructive",
      title: "Giao dịch chưa hoàn tất",
      description: "Khoản thanh toán không thành công. Booking chỉ được giữ đến thời hạn đã thông báo.",
    }
  }
  if (state === "cancelled") {
    return {
      icon: Ban,
      badge: "Đã hủy thanh toán",
      badgeVariant: "warning",
      title: "Bạn đã hủy giao dịch",
      description: "Không có khoản tiền nào được backend ghi nhận cho lần thanh toán này.",
    }
  }
  if (state === "missing") {
    return {
      icon: ReceiptText,
      badge: "Thiếu thông tin",
      badgeVariant: "outline",
      title: "Không tìm thấy phiên thanh toán",
      description: "Phiên checkout có thể đã hết hoặc được mở ở một trình duyệt khác.",
    }
  }
  if (state === "pending") {
    return {
      icon: Clock3,
      badge: "Đang xác minh",
      badgeVariant: "warning",
      title: "Giao dịch đang được xử lý",
      description: "Backend chưa nhận được callback hợp lệ từ gateway.",
    }
  }
  return {
    icon: Loader2,
    badge: "Đang tải",
    badgeVariant: "outline",
    title: "Đang kiểm tra thanh toán",
    description: "Vui lòng giữ nguyên trang trong khi hệ thống xác minh kết quả.",
  }
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <span className="text-muted-foreground">{label}</span>
      <span className="truncate font-medium">{value}</span>
    </div>
  )
}
