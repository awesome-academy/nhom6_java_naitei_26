"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useMemo, useState, type ReactNode } from "react"
import {
  Ban,
  CheckCircle2,
  Clock3,
  Copy,
  Loader2,
  ReceiptText,
  ShieldCheck,
  XCircle,
} from "lucide-react"
import { QRCodeSVG } from "qrcode.react"
import { toast } from "sonner"

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
import { Separator } from "@/components/ui/separator"
import { cancelPayment, getPayment, submitMockWalletResult } from "@/lib/api/payment"
import { loadPaymentCheckout, type PaymentCheckoutSession } from "@/lib/payment-checkout"
import type { MockWalletResult, PaymentStatusResponse } from "@/types/payment"

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(value)
}

function formatRemainingTime(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`
}

function getErrorMessage(error: unknown) {
  if (!(error instanceof Error)) return "Không thể xử lý mock payment."
  return error.message || "Không thể xử lý mock payment."
}

function getRemainingSeconds(expiresAt: string | null) {
  if (!expiresAt) return 0
  return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000))
}

export function MockWalletCheckout({
  paymentCode,
  bookingPublicId,
}: {
  paymentCode: string
  bookingPublicId?: string
}) {
  const router = useRouter()
  const [checkout, setCheckout] = useState<PaymentCheckoutSession | null>(null)
  const [payment, setPayment] = useState<PaymentStatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [action, setAction] = useState<MockWalletResult | "CANCELLED" | null>(null)
  const [remainingSeconds, setRemainingSeconds] = useState(0)

  useEffect(() => {
    let ignore = false

    async function loadCheckout() {
      const storedCheckout = loadPaymentCheckout()
      const resolvedBookingId = bookingPublicId || storedCheckout?.bookingPublicId
      if (
        !storedCheckout ||
        storedCheckout.paymentCode !== paymentCode ||
        !resolvedBookingId ||
        storedCheckout.bookingPublicId !== resolvedBookingId ||
        storedCheckout.method !== "E_WALLET"
      ) {
        if (!ignore) {
          setError("Không tìm thấy phiên mock payment phù hợp. Vui lòng tạo lại payment từ booking wizard.")
          setIsLoading(false)
        }
        return
      }

      try {
        const currentPayment = await getPayment(resolvedBookingId, paymentCode)
        if (ignore) return
        setCheckout(storedCheckout)
        setPayment(currentPayment)
        setRemainingSeconds(getRemainingSeconds(currentPayment.expiresAt))

        if (currentPayment.status === "SUCCEEDED") {
          router.replace(buildResultUrl("success", resolvedBookingId, paymentCode))
        } else if (["FAILED", "EXPIRED"].includes(currentPayment.status)) {
          router.replace(buildResultUrl("error", resolvedBookingId, paymentCode))
        } else if (currentPayment.status === "CANCELLED") {
          router.replace(buildResultUrl("cancel", resolvedBookingId, paymentCode))
        }
      } catch (loadError) {
        if (!ignore) setError(getErrorMessage(loadError))
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }

    loadCheckout()
    return () => {
      ignore = true
    }
  }, [bookingPublicId, paymentCode, router])

  useEffect(() => {
    if (!payment?.expiresAt) return

    const timer = window.setInterval(() => {
      setRemainingSeconds(getRemainingSeconds(payment.expiresAt))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [payment?.expiresAt])

  const isActive = useMemo(
    () => Boolean(payment && ["PENDING", "PROCESSING"].includes(payment.status) && remainingSeconds > 0),
    [payment, remainingSeconds]
  )

  async function submitResult(result: MockWalletResult) {
    if (!checkout) return
    setAction(result)
    setError(null)
    try {
      await submitMockWalletResult(checkout.bookingPublicId, paymentCode, result)
      router.push(
        buildResultUrl(result === "SUCCEEDED" ? "success" : "error", checkout.bookingPublicId, paymentCode)
      )
    } catch (submitError) {
      const message = getErrorMessage(submitError)
      setError(message)
      toast.error(message)
      setAction(null)
    }
  }

  async function cancelCheckout() {
    if (!checkout) return
    setAction("CANCELLED")
    setError(null)
    try {
      await cancelPayment(checkout.bookingPublicId, paymentCode)
      router.push(buildResultUrl("cancel", checkout.bookingPublicId, paymentCode))
    } catch (cancelError) {
      const message = getErrorMessage(cancelError)
      setError(message)
      toast.error(message)
      setAction(null)
    }
  }

  async function copyPaymentCode() {
    await navigator.clipboard.writeText(paymentCode)
    toast.success("Đã sao chép mã thanh toán")
  }

  if (isLoading) {
    return (
      <PaymentShell>
        <Card className="mx-auto w-full max-w-xl">
          <CardHeader>
            <CardTitle>Đang tải mock payment</CardTitle>
            <CardDescription>Hệ thống đang kiểm tra phiên thanh toán.</CardDescription>
          </CardHeader>
          <CardContent className="flex items-center gap-3">
            <Loader2 className="animate-spin" />
            Vui lòng chờ...
          </CardContent>
        </Card>
      </PaymentShell>
    )
  }

  if (error && (!checkout || !payment)) {
    return (
      <PaymentShell>
        <div className="mx-auto flex w-full max-w-xl flex-col gap-4">
          <Alert variant="destructive">
            <ReceiptText />
            <AlertTitle>Không thể mở mock payment</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
          <Button asChild variant="outline" className="w-fit">
            <Link href="/profile/bookings">Quay lại danh sách booking</Link>
          </Button>
        </div>
      </PaymentShell>
    )
  }

  if (!checkout || !payment) return null

  return (
    <PaymentShell>
      <div className="flex flex-col gap-5">
        <div className="flex flex-col justify-between gap-3 md:flex-row md:items-end">
          <div className="flex flex-col gap-2">
            <Badge variant="outline" className="w-fit">Mock E-Wallet</Badge>
            <h1 className="font-serif text-3xl tracking-tight md:text-4xl">Quét QR để thanh toán</h1>
            <p className="text-muted-foreground">
              Trang mô phỏng dành cho môi trường phát triển, không phát sinh giao dịch thật.
            </p>
          </div>
          <Badge variant="warning">{payment.status}</Badge>
        </div>

        {error && (
          <Alert variant="destructive">
            <XCircle />
            <AlertTitle>Không thể xử lý kết quả</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="grid gap-6 lg:grid-cols-[360px_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>Thông tin booking</CardTitle>
              <CardDescription>{checkout.bookingCode}</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <SummaryRow label="Số tiền cọc" value={formatMoney(checkout.amount, checkout.currency)} emphasized />
              <SummaryRow label="Nhà cung cấp" value={checkout.provider} />
              <Separator />
              <div className="flex flex-col gap-2">
                <span className="text-sm text-muted-foreground">Mã thanh toán</span>
                <div className="flex items-center justify-between gap-3 rounded-md bg-muted p-3">
                  <span className="truncate font-mono text-sm font-medium">{paymentCode}</span>
                  <Button type="button" size="icon" variant="ghost" onClick={copyPaymentCode}>
                    <Copy />
                    <span className="sr-only">Sao chép mã thanh toán</span>
                  </Button>
                </div>
              </div>
              <Alert>
                <ShieldCheck />
                <AlertTitle>Mock gateway an toàn</AlertTitle>
                <AlertDescription>
                  Chỉ endpoint đã đăng nhập mới có thể gửi kết quả mô phỏng về backend.
                </AlertDescription>
              </Alert>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Mã QR thanh toán</CardTitle>
              <CardDescription>Quét QR hoặc dùng các nút mô phỏng bên dưới.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col items-center gap-5">
              {checkout.qrCodeValue ? (
                <div className="rounded-xl border bg-background p-5">
                  <QRCodeSVG
                    value={checkout.qrCodeValue}
                    size={220}
                    level="M"
                    title={`QR thanh toán ${paymentCode}`}
                  />
                </div>
              ) : (
                <Alert variant="destructive">
                  <ReceiptText />
                  <AlertTitle>Không có dữ liệu QR</AlertTitle>
                  <AlertDescription>Gateway không trả về nội dung QR cho payment này.</AlertDescription>
                </Alert>
              )}

              <div className="flex items-center gap-2">
                <Clock3 />
                <span className="text-sm text-muted-foreground">Hết hạn sau</span>
                <span className="font-mono text-xl font-semibold">{formatRemainingTime(remainingSeconds)}</span>
              </div>

              <div className="w-full rounded-md bg-muted p-3 text-center font-mono text-xs break-all">
                {checkout.qrCodeValue}
              </div>
            </CardContent>
            <CardFooter className="grid gap-3 sm:grid-cols-3">
              <Button
                type="button"
                variant="success"
                onClick={() => submitResult("SUCCEEDED")}
                disabled={!isActive || action !== null}
              >
                {action === "SUCCEEDED" ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <CheckCircle2 data-icon="inline-start" />}
                Thành công
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={() => submitResult("FAILED")}
                disabled={!isActive || action !== null}
              >
                {action === "FAILED" ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <XCircle data-icon="inline-start" />}
                Thất bại
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={cancelCheckout}
                disabled={!isActive || action !== null}
              >
                {action === "CANCELLED" ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Ban data-icon="inline-start" />}
                Hủy
              </Button>
            </CardFooter>
          </Card>
        </div>
      </div>
    </PaymentShell>
  )
}

function PaymentShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-muted/30">
      <SiteHeader />
      <main className="mx-auto w-full max-w-6xl px-6 py-8 lg:px-8">{children}</main>
    </div>
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
      <span className={emphasized ? "text-xl font-semibold" : "text-sm font-medium"}>{value}</span>
    </div>
  )
}

function buildResultUrl(
  outcome: "success" | "error" | "cancel",
  bookingPublicId: string,
  paymentCode: string
) {
  const params = new URLSearchParams({ bookingId: bookingPublicId, paymentCode })
  return `/payment/${outcome}?${params.toString()}`
}
