"use client"

import { useState } from "react"
import { Building2, CreditCard, Loader2, Smartphone } from "lucide-react"
import { toast } from "sonner"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { createStaffPayment } from "@/lib/api/booking-staff-api"
import {
  getOrCreateIdempotencyKey,
  redirectToPaymentCheckout,
} from "@/lib/payment-checkout"
import type { PaymentMethod } from "@/types/payment"

const PAYMENT_METHODS: Array<{
  value: PaymentMethod
  label: string
  description: string
  icon: typeof Smartphone
}> = [
  {
    value: "E_WALLET",
    label: "Ví điện tử",
    description: "Tạo QR để khách quét thanh toán.",
    icon: Smartphone,
  },
  {
    value: "INTERNET_BANKING",
    label: "Internet Banking",
    description: "Mở checkout của cổng thanh toán.",
    icon: Building2,
  },
  {
    value: "CARD",
    label: "Thẻ",
    description: "Mở checkout của cổng thanh toán.",
    icon: CreditCard,
  },
]

export function StaffBookingPaymentDialog({
  open,
  onOpenChange,
  bookingPublicId,
  bookingCode,
  amount,
  currency,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  bookingPublicId: string
  bookingCode: string
  amount: number
  currency: string
}) {
  const [method, setMethod] = useState<PaymentMethod>("E_WALLET")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function startPayment() {
    setIsSubmitting(true)
    setError(null)
    try {
      const idempotencyKey = getOrCreateIdempotencyKey(bookingPublicId, method)
      const payment = await createStaffPayment(bookingPublicId, method, idempotencyKey)
      if (payment.status !== "PENDING" && payment.status !== "PROCESSING") {
        throw new Error("Payment này không còn ở trạng thái chờ thanh toán.")
      }

      onOpenChange(false)
      redirectToPaymentCheckout(
        { publicId: bookingPublicId, bookingCode },
        payment,
        { staffBooking: true },
      )
    } catch (paymentError) {
      const message = paymentError instanceof Error
        ? paymentError.message
        : "Không thể tạo yêu cầu thanh toán."
      setError(message)
      toast.error(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Thanh toán booking {bookingCode}</DialogTitle>
          <DialogDescription>
            Chọn phương thức để tạo QR hoặc mở checkout cho khách thanh toán.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <Alert variant="destructive">
            <AlertTitle>Không thể tạo thanh toán</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="flex flex-col gap-4">
          <div className="rounded-lg bg-muted/50 p-4">
            <p className="text-sm text-muted-foreground">Số tiền cần thanh toán</p>
            <p className="mt-1 text-2xl font-semibold">
              {new Intl.NumberFormat("vi-VN", {
                style: "currency",
                currency,
                maximumFractionDigits: currency === "VND" ? 0 : 2,
              }).format(amount)}
            </p>
          </div>

          <ToggleGroup
            type="single"
            value={method}
            onValueChange={(value) => value && setMethod(value as PaymentMethod)}
            variant="outline"
            className="grid gap-2 sm:grid-cols-3"
            aria-label="Phương thức thanh toán"
          >
            {PAYMENT_METHODS.map((option) => {
              const Icon = option.icon
              return (
                <ToggleGroupItem key={option.value} value={option.value} className="h-auto min-h-24 flex-col gap-2 p-3">
                  <Icon className="size-5" />
                  <span className="text-sm font-medium">{option.label}</span>
                  <span className="text-center text-xs font-normal text-muted-foreground">
                    {option.description}
                  </span>
                </ToggleGroupItem>
              )
            })}
          </ToggleGroup>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            Hủy
          </Button>
          <Button type="button" onClick={() => void startPayment()} disabled={isSubmitting}>
            {isSubmitting ? <Loader2 data-icon="inline-start" className="animate-spin" /> : null}
            Tạo thanh toán
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
