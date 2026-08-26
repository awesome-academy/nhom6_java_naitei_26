"use client"

import { useMemo, useState } from "react"
import { Ban, Download, FileText, Loader2, ReceiptText } from "lucide-react"
import { toast } from "sonner"

import { InvoiceIssuanceDialog } from "@/components/admin/bookings/invoice-issuance-dialog"
import { InvoicePreview } from "@/components/admin/bookings/invoice-preview"
import { VoidInvoiceDialog } from "@/components/admin/bookings/void-invoice-dialog"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { getInvoicePdf } from "@/lib/api/invoices"
import type { BookingStaffDetail, InvoiceResponse } from "@/types/booking-staff"

interface InvoicePanelProps {
  booking: BookingStaffDetail
  canIssue: boolean
  canVoid: boolean
  onChanged: (invoice: InvoiceResponse, refresh?: boolean) => void
}

const statusLabels: Record<InvoiceResponse["status"], string> = {
  DRAFT: "Bản nháp",
  ISSUED: "Đã phát hành",
  VOID: "Đã hủy",
}

function statusVariant(status: InvoiceResponse["status"]): "secondary" | "confirmed" | "destructive" {
  if (status === "ISSUED") return "confirmed"
  if (status === "VOID") return "destructive"
  return "secondary"
}

function getDownloadError(error: unknown): string {
  const status = (error as { status?: number })?.status
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền tải PDF hóa đơn."
  if (status === 404) return "Không tìm thấy hóa đơn hoặc tệp PDF."
  if (status === 503) return "Dịch vụ lưu trữ PDF đang tạm thời không khả dụng."
  if (error instanceof Error && error.message) return error.message
  return "Không thể tạo liên kết tải PDF. Vui lòng thử lại."
}

export function InvoicePanel({ booking, canIssue, canVoid, onChanged }: InvoicePanelProps) {
  const [isIssuanceOpen, setIsIssuanceOpen] = useState(false)
  const [isVoidOpen, setIsVoidOpen] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)

  const invoice = useMemo(() => {
    const newestFirst = [...booking.invoices].reverse()
    return newestFirst.find((item) => item.status === "DRAFT")
      ?? newestFirst.find((item) => item.status === "ISSUED")
      ?? newestFirst[0]
      ?? null
  }, [booking.invoices])

  async function downloadPdf() {
    if (!invoice) return
    setIsDownloading(true)
    try {
      const pdf = await getInvoicePdf(invoice.publicId)
      const anchor = document.createElement("a")
      anchor.href = pdf.url
      anchor.target = "_blank"
      anchor.rel = "noopener noreferrer"
      anchor.click()
      toast.success("Đã tạo liên kết tải PDF")
    } catch (error) {
      console.error("Failed to get invoice PDF", { invoicePublicId: invoice.publicId, error })
      toast.error(getDownloadError(error))
    } finally {
      setIsDownloading(false)
    }
  }

  if (!invoice) {
    return (
      <Alert>
        <ReceiptText className="size-4" aria-hidden="true" />
        <AlertTitle>Chưa có hóa đơn</AlertTitle>
        <AlertDescription>
          Hóa đơn bản nháp được hệ thống tạo tự động sau khi checkout. Booking hiện tại vẫn có thể xem Folio ở tab bên cạnh.
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <>
      <Card>
        <CardHeader className="flex-row flex-wrap items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <CardTitle>Hóa đơn {invoice.invoiceNumber ?? "chưa phát hành"}</CardTitle>
              <Badge variant={statusVariant(invoice.status)}>{statusLabels[invoice.status]}</Badge>
            </div>
            <CardDescription>
              Dữ liệu hóa đơn là snapshot của Folio tại thời điểm checkout và các dòng điều chỉnh trên bản nháp.
            </CardDescription>
          </div>
          <div className="flex flex-wrap gap-2">
            {invoice.status === "DRAFT" && canIssue && (
              <Button onClick={() => setIsIssuanceOpen(true)}>
                <FileText data-icon="inline-start" />
                Xuất hóa đơn
              </Button>
            )}
            {(invoice.status === "ISSUED" || invoice.status === "VOID") && canIssue && (
              <Button variant="outline" onClick={downloadPdf} disabled={isDownloading}>
                {isDownloading ? (
                  <Loader2 data-icon="inline-start" className="animate-spin" />
                ) : (
                  <Download data-icon="inline-start" />
                )}
                Tải PDF
              </Button>
            )}
            {invoice.status === "ISSUED" && canVoid && (
              <Button variant="destructive" onClick={() => setIsVoidOpen(true)}>
                <Ban data-icon="inline-start" />
                Hủy hóa đơn
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {invoice.status === "DRAFT" && !canIssue && (
            <p className="mb-4 rounded-lg border bg-muted/40 p-3 text-sm text-muted-foreground">
              Bạn chỉ có quyền xem bản nháp; quyền invoice:issue là bắt buộc để chỉnh sửa và phát hành.
            </p>
          )}
          {invoice.status === "ISSUED" && !canIssue && !canVoid && (
            <p className="mb-4 rounded-lg border bg-muted/40 p-3 text-sm text-muted-foreground">
              Bạn đang xem hóa đơn ở chế độ chỉ đọc.
            </p>
          )}
          <InvoicePreview invoice={invoice} bookingCode={booking.bookingCode} />
        </CardContent>
      </Card>

      {invoice.status === "DRAFT" && (
        <InvoiceIssuanceDialog
          open={isIssuanceOpen}
          booking={booking}
          invoice={invoice}
          onOpenChange={setIsIssuanceOpen}
          onChanged={onChanged}
        />
      )}
      {invoice.status === "ISSUED" && (
        <VoidInvoiceDialog
          open={isVoidOpen}
          invoice={invoice}
          onOpenChange={setIsVoidOpen}
          onVoided={(voidedInvoice) => {
            onChanged(voidedInvoice, true)
            setIsVoidOpen(false)
            toast.success(`Đã hủy hóa đơn ${voidedInvoice.invoiceNumber}`)
          }}
        />
      )}
    </>
  )
}
