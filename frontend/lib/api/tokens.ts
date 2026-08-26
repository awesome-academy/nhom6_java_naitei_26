// Shared token utilities - no circular dependencies

export type AuthSessionScope = "customer" | "admin"

const TOKEN_KEYS: Record<AuthSessionScope, { accessToken: string; refreshToken: string }> = {
  customer: {
    accessToken: "customer_access_token",
    refreshToken: "customer_refresh_token",
  },
  admin: {
    accessToken: "admin_access_token",
    refreshToken: "admin_refresh_token",
  },
}

export function getAuthSessionScopeFromPathname(pathname: string): AuthSessionScope {
  return pathname.startsWith("/admin") || pathname.startsWith("/staff") ? "admin" : "customer"
}

export function getCurrentAuthSessionScope(): AuthSessionScope {
  if (typeof window === "undefined") {
    return "customer"
  }
  return getAuthSessionScopeFromPathname(window.location.pathname)
}

export function getTokenStorageKeys(scope: AuthSessionScope = getCurrentAuthSessionScope()) {
  return TOKEN_KEYS[scope]
}

export function storeTokens(
  accessToken: string,
  refreshToken: string,
  scope: AuthSessionScope = getCurrentAuthSessionScope()
) {
  if (typeof window !== "undefined") {
    const keys = getTokenStorageKeys(scope)
    localStorage.setItem(keys.accessToken, accessToken)
    localStorage.setItem(keys.refreshToken, refreshToken)
  }
}

export function clearTokens(scope: AuthSessionScope = getCurrentAuthSessionScope()) {
  if (typeof window !== "undefined") {
    const keys = getTokenStorageKeys(scope)
    localStorage.removeItem(keys.accessToken)
    localStorage.removeItem(keys.refreshToken)
  }
}

export function getStoredTokens(
  scope: AuthSessionScope = getCurrentAuthSessionScope()
): { accessToken: string | null; refreshToken: string | null } {
  if (typeof window === "undefined") {
    return { accessToken: null, refreshToken: null }
  }
  const keys = getTokenStorageKeys(scope)
  return {
    accessToken: localStorage.getItem(keys.accessToken),
    refreshToken: localStorage.getItem(keys.refreshToken),
  }
}
