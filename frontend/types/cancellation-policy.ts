export interface CancellationPolicyRule {
  minHoursBefore: number
  refundPercent: number
}

export interface CancellationPolicy {
  code: string
  name: string
  description: string | null
  noShowChargePercent: number
  priceAdjustmentPercent: number
  isDefault: boolean
  isActive: boolean
  rules: CancellationPolicyRule[]
  createdAt: string
  updatedAt: string
}

export interface CancellationPolicyCreateRequest {
  code: string
  name: string
  description: string | null
  noShowChargePercent: number
  priceAdjustmentPercent: number
  isDefault: boolean
  isActive: boolean
  rules: CancellationPolicyRule[]
}

export type CancellationPolicyUpdateRequest = Omit<
  CancellationPolicyCreateRequest,
  "code"
>
