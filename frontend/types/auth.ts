// Auth types matching backend DTOs

export interface UserSummary {
  publicId: string
  email: string
  fullName: string
  avatarUrl?: string | null
  status: UserStatus
  emailVerifiedAt: string | null
  roles: string[]
  permissions: string[]
}

export type UserStatus =
  | "PENDING_VERIFICATION"
  | "ACTIVE"
  | "SUSPENDED"
  | "DEACTIVATED"

export interface AuthResponse {
  tokenType: string
  accessToken: string
  accessTokenExpiresInSeconds: number
  refreshToken: string
  refreshTokenExpiresInSeconds: number
  user: UserSummary
}

export interface AuthMessageResponse {
  message: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface EmailVerificationRequest {
  token: string
}

export interface EmailVerificationResendRequest {
  email: string
}

export interface PasswordResetEmailRequest {
  email: string
}

export interface PasswordResetConfirmRequest {
  token: string
  newPassword: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}

export interface OAuthGoogleRequest {
  providerUserId: string
  email: string
  fullName: string
}

// Form schemas
export interface LoginFormData {
  email: string
  password: string
  remember?: boolean
}

export interface RegisterFormData {
  fullName: string
  email: string
  phone?: string
  password: string
  confirmPassword: string
  terms: boolean
}

export interface ForgotPasswordFormData {
  email: string
}

export interface ResetPasswordFormData {
  password: string
  confirmPassword: string
}

// Auth state
export interface AuthState {
  user: UserSummary | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
  isLoading: boolean
}
