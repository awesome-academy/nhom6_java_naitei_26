"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { ArrowLeft, Download, Loader2, ReceiptText, RefreshCw } from "lucide-react"
import { toast } from "sonner"

import { InvoicePreview } from "@/components/invoice/invoice-preview"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge, getPaymentStatusVariant } from "@/components/ui/badge"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getMyBookingDetail } from "@/lib/api/booking"
import { getCustomerInvoice, getCustomerInvoicePdf } from "@/lib/api/customer-invoices"
import type { BookingDetail } from "@/types/booking"
import type { InvoiceResponse } from "@/types/invoice"

interface CustomerInvoiceViewProps {
  bookingPublicId: string
}

const paymentStatusLabels: Record<InvoiceResponse["paymentStatus"], string> = {
  UNPAID: "Chưa thanh toán",
  PARTIALLY_PAID: "Thanh toán một phần",
  PAID: "Đã thanh toán",
  PARTIALLY_REFUNDED: "Hoàn một phần",
  REFUNDED: "Đã hoàn tiền",
}

function getStatus(error: unknown): number | undefined {
  return (error as { status?: number })?.status
}

function getPageError(error: unknown): string {
  const status = getStatus(error)
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền xem hóa đơn của booking này."
  if (status === 404) return "Booking không tồn tại hoặc không thuộc tài khoản của bạn."
  if (error instanceof Error && error.message) return error.message
  return "Không thể tải thông tin hóa đơn. Vui lòng thử lại."
}

function getInvoiceError(error: unknown): string {
  const status = getStatus(error)
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền xem hóa đơn này."
  if (error instanceof Error && error.message) return error.message
  return "Không thể tải hóa đơn. Vui lòng thử lại."
}

function getDownloadError(error: unknown): string {
  const status = getStatus(error)
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền tải PDF hóa đơn này."
  if (status === 404) return "Không tìm thấy hóa đơn hoặc hóa đơn không thuộc booking này."
  if (status === 503) return "Dịch vụ lưu trữ PDF đang tạm thời không khả dụng."
  if (error instanceof Error && error.message) return error.message
  return "Không thể tạo liên kết tải PDF. Vui lòng thử lại."
}

export function CustomerInvoiceView({ bookingPublicId }: CustomerInvoiceViewProps) {
  const [detail, setDetail] = useState<BookingDetail | null>(null)
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)
  const [invoiceError, setInvoiceError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isDownloading, setIsDownloading] = useState(false)
  const [reloadVersion, setReloadVersion] = useState(0)

  useEffect(() => {
    let ignore = false

    Promise.allSettled([
      getMyBookingDetail(bookingPublicId),
      getCustomerInvoice(bookingPublicId),
    ]).then(([bookingResult, invoiceResult]) => {
      if (ignore) return

      if (bookingResult.status === "rejected") {
        setDetail(null)
        setInvoice(null)
        setPageError(getPageError(bookingResult.reason))
        setIsLoading(false)
        return
      }

      setDetail(bookingResult.value)
      if (invoiceResult.status === "fulfilled") {
        setInvoice(invoiceResult.value)
      } else if (getStatus(invoiceResult.reason) === 404) {
        setInvoice(null)
      } else {
        setInvoice(null)
        setInvoiceError(getInvoiceError(invoiceResult.reason))
      }
      setIsLoading(false)
    })

    return () => {
      ignore = true
    }
  }, [bookingPublicId, reloadVersion])

  function retryLoad() {
    setIsLoading(true)
    setPageError(null)
    setInvoiceError(null)
    setReloadVersion((current) => current + 1)
  }

  async function downloadPdf() {
    if (!invoice) return

    setIsDownloading(true)
    try {
      const pdf = await getCustomerInvoicePdf(bookingPublicId, invoice.publicId)
      const anchor = document.createElement("a")
      anchor.href = pdf.url
      anchor.target = "_blank"
      anchor.rel = "noopener noreferrer"
      anchor.click()
      toast.success("Đã tạo liên kết tải PDF")
    } catch (error) {
      console.error("Failed to get customer invoice PDF", {
        bookingPublicId,
        invoicePublicId: invoice.publicId,
        error,
      })
      toast.error(getDownloadError(error))
    } finally {
      setIsDownloading(false)
    }
  }

  if (isLoading) return <CustomerInvoiceSkeleton />

  if (pageError || !detail) {
    return (
      <div className="flex flex-col gap-6">
        <InvoiceBreadcrumb bookingPublicId={bookingPublicId} bookingCode="Không tìm thấy" />
        <Alert variant="destructive">
          <ReceiptText aria-hidden="true" />
          <AlertTitle>Không thể mở hóa đơn</AlertTitle>
          <AlertDescription>{pageError ?? "Không tìm thấy dữ liệu booking."}</AlertDescription>
        </Alert>
        <div>
          <Button asChild variant="outline">
            <Link href="/profile/bookings">
              <ArrowLeft data-icon="inline-start" />
              Quay lại danh sách
            </Link>
          </Button>
        </div>
      </div>
    )
  }

  const { booking } = detail

  return (
    <div className="flex flex-col gap-6">
      <InvoiceBreadcrumb
        bookingPublicId={booking.publicId}
        bookingCode={booking.bookingCode}
      />

      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start">
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight">Hóa đơn</h1>
            {invoice && (
              <Badge variant={invoice.status === "VOID" ? "destructive" : "confirmed"}>
                {invoice.status === "VOID" ? "Đã hủy" : "Đã phát hành"}
              </Badge>
            )}
            {invoice && (
              <Badge variant={getPaymentStatusVariant(invoice.paymentStatus)}>
                {paymentStatusLabels[invoice.paymentStatus]}
              </Badge>
            )}
          </div>
          <p className="font-mono text-sm text-muted-foreground">{booking.bookingCode}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {invoice && (
            <Button onClick={downloadPdf} disabled={isDownloading}>
              {isDownloading ? (
                <Loader2 data-icon="inline-start" className="animate-spin" />
              ) : (
                <Download data-icon="inline-start" />
              )}
              Tải PDF
            </Button>
          )}
          <Button asChild variant="outline">
            <Link href={`/profile/bookings/${encodeURIComponent(booking.publicId)}`}>
              <ArrowLeft data-icon="inline-start" />
              Chi tiết booking
            </Link>
          </Button>
        </div>
      </div>

      {invoiceError ? (
        <Alert variant="destructive">
          <ReceiptText aria-hidden="true" />
          <AlertTitle>Không thể tải hóa đơn</AlertTitle>
          <AlertDescription className="flex flex-col items-start gap-3">
            <span>{invoiceError}</span>
            <Button variant="outline" size="sm" onClick={retryLoad}>
              <RefreshCw data-icon="inline-start" />
              Thử lại
            </Button>
          </AlertDescription>
        </Alert>
      ) : invoice ? (
        <Card>
          <CardHeader>
            <CardTitle>{invoice.invoiceNumber ?? "Hóa đơn"}</CardTitle>
            <CardDescription>
              Hóa đơn là dữ liệu snapshot chỉ đọc tại thời điểm phát hành.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {invoice.status === "VOID" && (
              <Alert variant="destructive" className="mb-6">
                <ReceiptText aria-hidden="true" />
                <AlertTitle>Hóa đơn đã hủy</AlertTitle>
                <AlertDescription>
                  Bản hóa đơn này được giữ lại để đối chiếu và không thể chỉnh sửa.
                </AlertDescription>
              </Alert>
            )}
            <InvoicePreview invoice={invoice} bookingCode={booking.bookingCode} />
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Hóa đơn chưa được phát hành</CardTitle>
            <CardDescription>
              Hóa đơn chỉ xuất hiện sau khi nhân viên hoàn tất checkout và phát hành bản chính thức.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Alert>
              <ReceiptText aria-hidden="true" />
              <AlertTitle>Chưa có hóa đơn để xem</AlertTitle>
              <AlertDescription>
                Bản nháp không được hiển thị cho Customer. Vui lòng quay lại sau khi hóa đơn được phát hành.
              </AlertDescription>
            </Alert>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function InvoiceBreadcrumb({
  bookingPublicId,
  bookingCode,
}: {
  bookingPublicId: string
  bookingCode: string
}) {
  return (
    <Breadcrumb>
      <BreadcrumbList>
        <BreadcrumbItem>
          <BreadcrumbLink href="/profile/bookings">Đơn đặt phòng</BreadcrumbLink>
        </BreadcrumbItem>
        <BreadcrumbSeparator />
        <BreadcrumbItem>
          <BreadcrumbLink href={`/profile/bookings/${encodeURIComponent(bookingPublicId)}`}>
            {bookingCode}
          </BreadcrumbLink>
        </BreadcrumbItem>
        <BreadcrumbSeparator />
        <BreadcrumbItem>
          <BreadcrumbPage>Hóa đơn</BreadcrumbPage>
        </BreadcrumbItem>
      </BreadcrumbList>
    </Breadcrumb>
  )
}

function CustomerInvoiceSkeleton() {
  return (
    <div className="flex flex-col gap-6" aria-label="Đang tải hóa đơn">
      <Skeleton className="h-5 w-72" />
      <div className="flex flex-col gap-3">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-36" />
      </div>
      <Skeleton className="h-[640px] w-full" />
    </div>
  )
}
