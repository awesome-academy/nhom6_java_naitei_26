import { apiClient } from "@/lib/api/client"
import type { InvoicePdfResponse, InvoiceResponse } from "@/types/invoice"

function bookingInvoicePath(bookingPublicId: string): string {
  return `/api/bookings/${encodeURIComponent(bookingPublicId)}`
}

export function getCustomerInvoice(bookingPublicId: string): Promise<InvoiceResponse> {
  return apiClient.get<InvoiceResponse>(`${bookingInvoicePath(bookingPublicId)}/invoice`)
}

export function getCustomerInvoicePdf(
  bookingPublicId: string,
  invoicePublicId: string
): Promise<InvoicePdfResponse> {
  return apiClient.get<InvoicePdfResponse>(
    `${bookingInvoicePath(bookingPublicId)}/invoices/${encodeURIComponent(invoicePublicId)}/pdf`
  )
}
