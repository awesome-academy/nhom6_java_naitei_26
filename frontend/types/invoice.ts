import type { InvoiceResponse } from "@/types/booking-staff"

export interface InvoiceBuyerUpdateRequest {
  buyerName: string
  buyerAddress: string | null
  buyerTaxCode: string | null
  buyerEmail: string | null
}

export interface InvoiceAdjustmentRequest {
  description: string
  amount: number
}

export interface InvoiceVoidRequest {
  reason: string
  createReplacement: false
}

export interface InvoiceVoidResponse {
  voidedInvoice: InvoiceResponse
  replacementInvoice: InvoiceResponse | null
}

export interface InvoicePdfResponse {
  url: string
  expiresAt: string
}
