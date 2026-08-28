"use client"

import { format } from "date-fns"
import { vi } from "date-fns/locale"
import { Trash2 } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { cn } from "@/lib/utils"
import type { InvoiceItemResponse, InvoiceResponse } from "@/types/invoice"

export interface InvoiceBuyerPreview {
  buyerName: string
  buyerAddress: string
  buyerTaxCode: string
  buyerEmail: string
}

interface InvoicePreviewProps {
  invoice: InvoiceResponse
  buyer?: InvoiceBuyerPreview
  bookingCode?: string
  className?: string
  onRemoveAdjustment?: (item: InvoiceItemResponse) => void
}

const lineTypeLabels: Record<InvoiceResponse["items"][number]["lineType"], string> = {
  ROOM: "Tiền phòng",
  SERVICE: "Dịch vụ",
  ADJUSTMENT: "Điều chỉnh",
}

function formatMoney(value: number, currency: string): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatIssuedAt(value: string | null): string {
  return value ? format(new Date(value), "dd/MM/yyyy HH:mm", { locale: vi }) : "Chưa phát hành"
}

export function InvoicePreview({
  invoice,
  buyer,
  bookingCode,
  className,
  onRemoveAdjustment,
}: InvoicePreviewProps) {
  const shownBuyer = buyer ?? {
    buyerName: invoice.buyerName,
    buyerAddress: invoice.buyerAddress ?? "",
    buyerTaxCode: invoice.buyerTaxCode ?? "",
    buyerEmail: invoice.buyerEmail ?? "",
  }

  return (
    <article
      className={cn(
        "mx-auto w-full max-w-3xl rounded-xl border bg-background p-5 shadow-sm sm:p-8",
        className
      )}
      aria-label="Bản xem trước hóa đơn"
    >
      <header className="flex flex-col justify-between gap-5 sm:flex-row sm:items-start">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">
            Hotel Management
          </p>
          <h3 className="mt-2 text-2xl font-bold tracking-tight">HÓA ĐƠN</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Booking {bookingCode ?? invoice.bookingPublicId}
          </p>
        </div>
        <div className="text-left sm:text-right">
          <Badge variant={invoice.status === "VOID" ? "destructive" : "secondary"}>
            {invoice.status === "DRAFT"
              ? "Bản nháp"
              : invoice.status === "ISSUED"
                ? "Đã phát hành"
                : "Đã hủy"}
          </Badge>
          <p className="mt-3 font-mono text-sm font-semibold">
            {invoice.invoiceNumber ?? "CHƯA CÓ SỐ HÓA ĐƠN"}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            {formatIssuedAt(invoice.issuedAt)}
          </p>
        </div>
      </header>

      <Separator className="my-6" />

      <section>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Thông tin người mua
        </p>
        <div className="mt-3 grid gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
          <p><span className="text-muted-foreground">Tên:</span> {shownBuyer.buyerName || "—"}</p>
          <p><span className="text-muted-foreground">Mã số thuế:</span> {shownBuyer.buyerTaxCode || "—"}</p>
          <p><span className="text-muted-foreground">Email:</span> {shownBuyer.buyerEmail || "—"}</p>
          <p><span className="text-muted-foreground">Địa chỉ:</span> {shownBuyer.buyerAddress || "—"}</p>
        </div>
      </section>

      <div className="mt-6 overflow-x-auto rounded-lg border">
        <table className="w-full min-w-[680px] text-sm">
          <thead className="bg-muted/60 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3">Nội dung</th>
              <th className="px-3 py-3 text-right">SL</th>
              <th className="px-3 py-3 text-right">Đơn giá</th>
              <th className="px-3 py-3 text-right">Thuế</th>
              <th className="px-4 py-3 text-right">Thành tiền</th>
              {onRemoveAdjustment && <th className="w-12 px-2 py-3" aria-label="Thao tác" />}
            </tr>
          </thead>
          <tbody className="divide-y">
            {invoice.items.map((item) => (
              <tr key={item.id}>
                <td className="px-4 py-3">
                  <p className="font-medium">{item.description}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {lineTypeLabels[item.lineType]}
                  </p>
                </td>
                <td className="px-3 py-3 text-right tabular-nums">{item.quantity}</td>
                <td className="px-3 py-3 text-right tabular-nums">
                  {formatMoney(item.unitPrice, invoice.currency)}
                </td>
                <td className="px-3 py-3 text-right tabular-nums">
                  {formatMoney(item.taxAmount, invoice.currency)}
                </td>
                <td className="px-4 py-3 text-right font-medium tabular-nums">
                  {formatMoney(item.lineTotal, invoice.currency)}
                </td>
                {onRemoveAdjustment && (
                  <td className="px-2 py-3 text-right">
                    {item.lineType === "ADJUSTMENT" && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => onRemoveAdjustment(item)}
                        aria-label={`Xóa dòng điều chỉnh ${item.description}`}
                        title="Xóa dòng điều chỉnh"
                      >
                        <Trash2 data-icon="inline-start" />
                      </Button>
                    )}
                  </td>
                )}
              </tr>
            ))}
            {invoice.items.length === 0 && (
              <tr>
                <td colSpan={onRemoveAdjustment ? 6 : 5} className="px-4 py-10 text-center text-muted-foreground">
                  Hóa đơn chưa có dòng chi tiết.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <footer className="mt-6 ml-auto grid w-full max-w-sm grid-cols-2 gap-x-8 gap-y-2 text-sm">
        <span className="text-muted-foreground">Tạm tính</span>
        <span className="text-right">{formatMoney(invoice.subtotal, invoice.currency)}</span>
        <span className="text-muted-foreground">Giảm giá</span>
        <span className="text-right">−{formatMoney(invoice.discountTotal, invoice.currency)}</span>
        <span className="text-muted-foreground">Thuế</span>
        <span className="text-right">{formatMoney(invoice.taxTotal, invoice.currency)}</span>
        <span className="border-t pt-3 text-base font-semibold">Tổng cộng</span>
        <span className="border-t pt-3 text-right text-lg font-bold text-primary">
          {formatMoney(invoice.totalAmount, invoice.currency)}
        </span>
      </footer>
    </article>
  )
}
