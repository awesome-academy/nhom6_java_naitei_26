import { apiClient } from "@/lib/api/client"

export interface CustomerProfile {
  publicId: string
  email: string
  phone: string | null
  fullName: string
  dateOfBirth: string | null
  gender: string | null
  nationality: string | null
  province: string | null
  addressLine: string | null
  country: string | null
  avatarUrl: string | null
  emailVerified: boolean
  joinedAt: string
  loyaltyPoints: number
  totalStays: number
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface CustomerProfileUpdateRequest {
  dateOfBirth?: string | null
  gender?: string | null
  nationality?: string | null
  addressLine?: string | null
  province?: string | null
  country?: string | null
  notes?: string | null
}

export interface Province {
  id: string
  name: string
}

export async function getCustomerProfile(): Promise<CustomerProfile> {
  return apiClient.get<CustomerProfile>("/api/customer-profiles/me")
}

export async function updateCustomerProfile(
  data: CustomerProfileUpdateRequest
): Promise<CustomerProfile> {
  return apiClient.patch<CustomerProfile>("/api/customer-profiles/me", data)
}

export async function getProvinces(): Promise<Province[]> {
  return apiClient.get<Province[]>("/api/vn/provinces")
}
