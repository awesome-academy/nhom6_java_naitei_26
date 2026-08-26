import { apiClient } from "./client"
import {
  storeTokens,
  clearTokens,
  getStoredTokens,
  type AuthSessionScope,
} from "./tokens"
import type {
  AuthMessageResponse,
  AuthResponse,
  EmailVerificationRequest,
  EmailVerificationResendRequest,
  LoginRequest,
  LogoutRequest,
  OAuthGoogleRequest,
  PasswordResetConfirmRequest,
  PasswordResetEmailRequest,
  RefreshTokenRequest,
  RegisterRequest,
} from "@/types/auth"

// Token storage helpers
const OAUTH_STATE_KEY = "oauth_google_state"

export { storeTokens, clearTokens, getStoredTokens }

export function initAuthFromStorage(scope?: AuthSessionScope) {
  const { accessToken } = getStoredTokens(scope)
  if (accessToken) {
    apiClient.setAccessToken(accessToken, scope)
  } else {
    apiClient.setAccessToken(null, scope)
  }
}

// Auth API functions
export async function login(data: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/auth/login", data)
  storeTokens(response.accessToken, response.refreshToken)
  return response
}

export async function register(data: RegisterRequest): Promise<AuthMessageResponse> {
  return apiClient.post<AuthMessageResponse>("/api/auth/register", data)
}

export async function verifyEmail(data: EmailVerificationRequest): Promise<AuthMessageResponse> {
  return apiClient.post<AuthMessageResponse>("/api/auth/verify-email", data)
}

export async function resendEmailVerification(
  data: EmailVerificationResendRequest
): Promise<AuthMessageResponse> {
  return apiClient.post<AuthMessageResponse>("/api/auth/verify-email/resend", data)
}

export async function requestPasswordReset(
  data: PasswordResetEmailRequest
): Promise<AuthMessageResponse> {
  return apiClient.post<AuthMessageResponse>("/api/auth/password-reset/request", data)
}

export async function resetPassword(
  data: PasswordResetConfirmRequest
): Promise<AuthMessageResponse> {
  return apiClient.post<AuthMessageResponse>("/api/auth/password-reset/confirm", data)
}

export async function acceptStaffInvitation(data: { token: string; newPassword: string }): Promise<AuthMessageResponse> {
  return apiClient.post<AuthMessageResponse>("/api/auth/staff-invitation/accept", data)
}

export async function refreshToken(data: RefreshTokenRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/auth/refresh", data)
  storeTokens(response.accessToken, response.refreshToken)
  return response
}

export async function logout(data: LogoutRequest): Promise<void> {
  await apiClient.post<void>("/api/auth/logout", data)
  clearTokens()
}

export async function loginWithGoogle(
  data: OAuthGoogleRequest
): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/auth/oauth/google", data)
  storeTokens(response.accessToken, response.refreshToken)
  return response
}

// OAuth Google functions
export interface OAuthAuthorizeResponse {
  authorizationUrl: string
  state: string
}

export interface OAuthCallbackData {
  code: string
  state?: string
}

export async function getGoogleOAuthUrl(): Promise<OAuthAuthorizeResponse> {
  const response = await apiClient.get<OAuthAuthorizeResponse>("/api/auth/oauth/google/authorize")
  // Store state for CSRF protection
  if (typeof window !== "undefined") {
    localStorage.setItem(OAUTH_STATE_KEY, response.state)
  }
  return response
}

export async function handleGoogleOAuthCallback(
  data: OAuthCallbackData
): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>("/api/auth/oauth/google/callback", data)
  storeTokens(response.accessToken, response.refreshToken)
  // Clear stored state
  if (typeof window !== "undefined") {
    localStorage.removeItem(OAUTH_STATE_KEY)
  }
  return response
}

export async function getOAuthStatus(): Promise<{ configured: boolean; provider: string }> {
  return apiClient.get<{ configured: boolean; provider: string }>("/api/auth/oauth/google/status")
}

export function clearOAuthState() {
  if (typeof window !== "undefined") {
    localStorage.removeItem(OAUTH_STATE_KEY)
  }
}
