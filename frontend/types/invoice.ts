export interface InvoiceItemResponse {
  id: number
  lineType: "ROOM" | "SERVICE" | "ADJUSTMENT"
  description: string
  quantity: number
  unitPrice: number
  lineSubtotal: number
  discountAmount: number
  taxPercent: number
  taxAmount: number
  lineTotal: number
  referenceType: string | null
  referenceId: number | null
  sortOrder: number
}

export interface InvoiceResponse {
  publicId: string
  invoiceNumber: string | null
  bookingPublicId: string
  status: "DRAFT" | "ISSUED" | "VOID"
  paymentStatus: "UNPAID" | "PARTIALLY_PAID" | "PAID" | "PARTIALLY_REFUNDED" | "REFUNDED"
  issuedAt: string | null
  issuedBy: number | null
  buyerName: string
  buyerAddress: string | null
  buyerTaxCode: string | null
  buyerEmail: string | null
  subtotal: number
  discountTotal: number
  taxTotal: number
  totalAmount: number
  paidAmount: number
  refundedAmount: number
  currency: string
  items: InvoiceItemResponse[]
  createdAt: string
  updatedAt: string
}

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
