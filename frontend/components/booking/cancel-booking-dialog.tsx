"use client"

import { useEffect, useState } from "react"
import { Loader2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { cancelBooking } from "@/lib/api/booking"
import { previewRefund } from "@/lib/api/refund"
import type { Booking } from "@/types/booking"
import type { RefundPreviewResponse } from "@/types/refund"

function formatMoney(value: number | string, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(Number(value) || 0)
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

interface CancelBookingDialogProps {
  open: boolean
  booking: Booking
  onOpenChange: (open: boolean) => void
  onCancelled: (result: { booking: Booking }) => void
}

export function CancelBookingDialog({ open, booking, onOpenChange, onCancelled }: CancelBookingDialogProps) {
  const [preview, setPreview] = useState<RefundPreviewResponse | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [isLoadingPreview, setIsLoadingPreview] = useState(false)
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [isConfirming, setIsConfirming] = useState(false)

  useEffect(() => {
    if (!open) return

    let ignore = false

    async function loadPreview() {
      setPreview(null)
      setPreviewError(null)
      setConfirmError(null)
      setIsLoadingPreview(true)
      try {
        const response = await previewRefund(booking.publicId)
        if (!ignore) setPreview(response)
      } catch (error) {
        if (!ignore) setPreviewError(getErrorMessage(error, "Không thể ước tính số tiền hoàn."))
      } finally {
        if (!ignore) setIsLoadingPreview(false)
      }
    }

    loadPreview()

    return () => {
      ignore = true
    }
  }, [open, booking.publicId])

  async function confirmCancel() {
    if (isConfirming) return
    setIsConfirming(true)
    setConfirmError(null)
    try {
      const updatedBooking = await cancelBooking(booking.publicId)
      onCancelled({ booking: updatedBooking })
      onOpenChange(false)
    } catch (cancelError) {
      setConfirmError(getErrorMessage(cancelError, "Không thể hủy booking."))
    } finally {
      setIsConfirming(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isConfirming && onOpenChange(nextOpen)}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Hủy booking {booking.bookingCode}</DialogTitle>
          <DialogDescription>
            Booking sẽ chuyển sang trạng thái đã hủy theo chính sách hủy hiện tại.
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-lg border bg-[var(--muted)]/40 p-4 text-sm">
          {isLoadingPreview && (
            <div className="flex items-center gap-2 text-[var(--muted-foreground)]">
              <Loader2 className="h-4 w-4 animate-spin" />
              Đang tính số tiền hoàn dự kiến...
            </div>
          )}

          {!isLoadingPreview && previewError && (
            <p className="text-[var(--muted-foreground)]">
              Không thể ước tính số tiền hoàn — bạn vẫn có thể hủy booking.
            </p>
          )}

          {!isLoadingPreview && preview && !preview.hasReceivedPayment && (
            <p className="text-[var(--muted-foreground)]">
              Chưa ghi nhận thanh toán cho booking này — sẽ không có hoàn tiền.
            </p>
          )}

          {!isLoadingPreview && preview && preview.hasReceivedPayment && preview.estimatedNetRefund <= 0 && (
            <p className="text-[var(--muted-foreground)]">
              Theo chính sách hủy hiện tại, bạn sẽ không được hoàn tiền khi hủy vào lúc này.
            </p>
          )}

          {!isLoadingPreview && preview && preview.hasReceivedPayment && preview.estimatedNetRefund > 0 && (
            <div className="flex flex-col gap-1">
              <p>
                Số tiền hoàn dự kiến:{" "}
                <span className="font-semibold text-foreground">
                  {formatMoney(preview.estimatedNetRefund, preview.currency)}
                </span>
              </p>
              <p className="text-xs text-[var(--muted-foreground)]">
                Số tiền thực tế có thể thay đổi nếu thời điểm hủy khác với lúc xem trước này.
              </p>
            </div>
          )}
        </div>

        {confirmError && <p className="text-sm text-destructive">{confirmError}</p>}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isConfirming}>
            Quay lại
          </Button>
          <Button type="button" variant="destructive" onClick={confirmCancel} disabled={isConfirming}>
            {isConfirming && <Loader2 data-icon="inline-start" className="animate-spin" />}
            Xác nhận hủy
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
