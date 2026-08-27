import { apiClient } from "@/lib/api/client"
import type {
  InvoiceAdjustmentRequest,
  InvoiceBuyerUpdateRequest,
  InvoicePdfResponse,
  InvoiceResponse,
  InvoiceVoidRequest,
  InvoiceVoidResponse,
} from "@/types/invoice"

function invoicePath(invoicePublicId: string): string {
  return `/api/invoices/${encodeURIComponent(invoicePublicId)}`
}

export function updateInvoiceBuyer(
  invoicePublicId: string,
  request: InvoiceBuyerUpdateRequest
): Promise<InvoiceResponse> {
  return apiClient.put<InvoiceResponse>(`${invoicePath(invoicePublicId)}/buyer`, request)
}

export function addInvoiceAdjustment(
  invoicePublicId: string,
  request: InvoiceAdjustmentRequest
): Promise<InvoiceResponse> {
  return apiClient.post<InvoiceResponse>(`${invoicePath(invoicePublicId)}/adjustments`, request)
}

export function removeInvoiceAdjustment(
  invoicePublicId: string,
  itemId: number
): Promise<InvoiceResponse> {
  return apiClient.delete<InvoiceResponse>(
    `${invoicePath(invoicePublicId)}/adjustments/${itemId}`
  )
}

export function issueInvoice(invoicePublicId: string): Promise<InvoiceResponse> {
  return apiClient.post<InvoiceResponse>(`${invoicePath(invoicePublicId)}/issue`, {})
}

export function voidInvoice(
  invoicePublicId: string,
  request: InvoiceVoidRequest
): Promise<InvoiceVoidResponse> {
  return apiClient.post<InvoiceVoidResponse>(`${invoicePath(invoicePublicId)}/void`, request)
}

export function getInvoicePdf(invoicePublicId: string): Promise<InvoicePdfResponse> {
  return apiClient.get<InvoicePdfResponse>(`${invoicePath(invoicePublicId)}/pdf`)
}
