import { apiClient } from "@/lib/api/client"
import type {
  CustomerAccountStatus,
  CustomerAccount,
  CustomerBooking,
  CustomerDetailResponse,
  CustomerListResponse,
  CustomerStatus,
} from "@/types/admin-customer"

const CUSTOMERS_ENDPOINT = "/api/users"

type CustomerListApiResponse = {
  items?: CustomerListResponse["items"]
  page?: number
  size?: number
  totalItems?: number
  totalPages?: number
  content?: CustomerListResponse["items"]
  number?: number
  totalElements?: number
}

export async function getCustomers(filters: {
  page: number
  search?: string
  status?: CustomerStatus | "ALL"
}): Promise<CustomerListResponse> {
  const params = new URLSearchParams({
    role: "CUSTOMER",
    page: String(filters.page),
  })
  if (filters.search?.trim()) params.set("search", filters.search.trim())
  if (filters.status && filters.status !== "ALL") params.set("status", filters.status)
  const response = await apiClient.get<
    CustomerListApiResponse | CustomerListResponse["items"]
  >(`${CUSTOMERS_ENDPOINT}?${params.toString()}`)

  if (Array.isArray(response)) {
    return {
      items: response,
      page: filters.page,
      size: 20,
      totalItems: response.length,
      totalPages: response.length === 0 ? 0 : Math.ceil(response.length / 20),
    }
  }

  return {
    items: Array.isArray(response.items)
      ? response.items
      : Array.isArray(response.content) ? response.content : [],
    page: response.page ?? response.number ?? filters.page,
    size: response.size ?? 20,
    totalItems: response.totalItems ?? response.totalElements ?? 0,
    totalPages: response.totalPages ?? 0,
  }
}

export async function getCustomer(publicId: string): Promise<CustomerDetailResponse> {
  const response = await apiClient.get<CustomerDetailResponse | CustomerAccount>(
    `${CUSTOMERS_ENDPOINT}/${encodeURIComponent(publicId)}`
  )
  if ("account" in response) return response
  return { account: response, profile: null }
}

export function getCustomerBookings(publicId: string): Promise<CustomerBooking[]> {
  return apiClient.get<CustomerBooking[]>(
    `${CUSTOMERS_ENDPOINT}/${encodeURIComponent(publicId)}/bookings`
  )
}

export function updateCustomerStatus(
  publicId: string,
  status: CustomerAccountStatus
) {
  return apiClient.patch<CustomerDetailResponse["account"]>(
    `${CUSTOMERS_ENDPOINT}/${encodeURIComponent(publicId)}/status`,
    { status }
  )
}
