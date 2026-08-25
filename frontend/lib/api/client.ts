import type { ApiErrorResponse } from "@/types/auth"
import {
  clearTokens,
  getCurrentAuthSessionScope,
  getStoredTokens,
  storeTokens,
  type AuthSessionScope,
} from "./tokens"

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"

export class ApiClient {
  private accessTokens: Record<AuthSessionScope, string | null> = {
    customer: null,
    admin: null,
  }

  setAccessToken(
    token: string | null,
    scope: AuthSessionScope = getCurrentAuthSessionScope()
  ) {
    this.accessTokens[scope] = token
  }

  getAccessToken(scope: AuthSessionScope = getCurrentAuthSessionScope()): string | null {
    if (this.accessTokens[scope]) {
      return this.accessTokens[scope]
    }
    const { accessToken } = getStoredTokens(scope)
    if (accessToken) {
      this.accessTokens[scope] = accessToken
      return accessToken
    }
    return null
  }

  private clearAccessToken(scope: AuthSessionScope) {
    this.accessTokens[scope] = null
  }

  private async refreshAccessToken(scope: AuthSessionScope): Promise<boolean> {
    const { refreshToken } = getStoredTokens(scope)
    if (!refreshToken) {
      return false
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      })

      if (!response.ok) {
        return false
      }

      const data = await response.json()
      storeTokens(data.accessToken, data.refreshToken, scope)
      this.accessTokens[scope] = data.accessToken
      return true
    } catch {
      return false
    }
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`
    const scope = getCurrentAuthSessionScope()

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

    let token = this.getAccessToken(scope)
    if (!token) {
      const refreshed = await this.refreshAccessToken(scope)
      if (refreshed) {
        token = this.getAccessToken(scope)
      }
    }

    if (token) {
      headers.set("Authorization", `Bearer ${token}`)
    }

    let response = await fetch(url, {
      ...options,
      headers,
    })

    // Auto-refresh token on 401
    if (response.status === 401 && token) {
      const refreshed = await this.refreshAccessToken(scope)
      if (refreshed) {
        const newToken = this.getAccessToken(scope)
        headers.set("Authorization", `Bearer ${newToken}`)
        response = await fetch(url, {
          ...options,
          headers,
        })
      } else {
        // Refresh failed - clear tokens
        clearTokens(scope)
        this.clearAccessToken(scope)
      }
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({
        message: "An error occurred",
        status: response.status,
      })) as ApiErrorResponse

      const error = new Error(errorData.message || "An error occurred") as Error & {
        status: number
        fieldErrors?: Record<string, string>
        response: ApiErrorResponse
        endpoint: string
      }
      error.status = errorData.status ?? response.status
      error.fieldErrors = errorData.fieldErrors
      error.response = errorData
      error.endpoint = endpoint
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

  patch<T>(endpoint: string, body: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: "PATCH",
      body: JSON.stringify(body),
    })
  }
}

export const apiClient = new ApiClient()
