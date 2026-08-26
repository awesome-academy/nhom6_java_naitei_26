export type ServiceCategory =
  | "FNB"
  | "LAUNDRY"
  | "SPA"
  | "TRANSPORT"
  | "MINIBAR"
  | "PENALTY"
  | "OTHER"

export interface ServiceItemOption {
  code: string
  name: string
  category: ServiceCategory
  unitPrice: number
  taxPercent: number
}

export interface FolioChargeCreateRequest {
  serviceItemCode: string
  quantity: number
}

export interface FolioChargeVoidRequest {
  reason: string
}
