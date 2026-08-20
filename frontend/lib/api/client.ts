import type { ApiErrorResponse } from "@/types/auth"

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"

export class ApiClient {
  private accessToken: string | null = null

  setAccessToken(token: string | null) {
    this.accessToken = token
  }

  getAccessToken(): string | null {
    if (!this.accessToken) {
      if (typeof window !== "undefined") {
        this.accessToken = localStorage.getItem("access_token")
      }
    }
    return this.accessToken
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`

    const headers = new Headers({
      "Content-Type": "application/json",
    })

    // Merge custom headers if provided
    if (options.headers) {
      const customHeaders = options.headers as HeadersInit
      if (Array.isArray(customHeaders)) {
        customHeaders.forEach(([key, value]) => headers.set(key, value))
      } else if (customHeaders instanceof Headers) {
        customHeaders.forEach((value, key) => headers.set(key, value))
      } else {
        Object.entries(customHeaders).forEach(([key, value]) => headers.set(key, value))
      }
    }

    const token = this.getAccessToken()
    if (token) {
      headers.set("Authorization", `Bearer ${token}`)
    }

    const response = await fetch(url, {
      ...options,
      headers,
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({
        message: "An error occurred",
        status: response.status,
      })) as ApiErrorResponse

      const error = new Error(errorData.message || "An error occurred") as Error & {
        status: number
        fieldErrors?: Record<string, string>
        response: ApiErrorResponse
      }
      error.status = errorData.status
      error.fieldErrors = errorData.fieldErrors
      error.response = errorData
      throw error
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return undefined as T
    }

    return response.json()
  }

  get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: "GET" })
  }

  post<T>(endpoint: string, body: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: "POST",
      body: JSON.stringify(body),
    })
  }

  put<T>(endpoint: string, body: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: "PUT",
      body: JSON.stringify(body),
    })
  }

  delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: "DELETE" })
  }
}

export const apiClient = new ApiClient()
