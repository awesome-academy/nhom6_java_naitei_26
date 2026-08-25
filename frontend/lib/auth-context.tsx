"use client"

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"
import { usePathname } from "next/navigation"
import type { UserSummary } from "@/types/auth"
import {
  clearTokens,
  getStoredTokens,
  initAuthFromStorage,
  storeTokens,
} from "@/lib/api/auth"
import { apiClient } from "@/lib/api/client"
import {
  getAuthSessionScopeFromPathname,
  type AuthSessionScope,
} from "@/lib/api/tokens"

interface AuthContextValue {
  user: UserSummary | null
  isAuthenticated: boolean
  isLoading: boolean
  setAuth: (user: UserSummary, accessToken: string, refreshToken: string) => void
  clearAuth: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

const USER_KEYS: Record<AuthSessionScope, string> = {
  customer: "customer_auth_user",
  admin: "admin_auth_user",
}

function getUserStorageKey(scope: AuthSessionScope): string {
  return USER_KEYS[scope]
}

function readStoredUser(scope: AuthSessionScope): UserSummary | null {
  if (typeof window === "undefined") return null
  const raw = localStorage.getItem(getUserStorageKey(scope))
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserSummary
  } catch {
    return null
  }
}

function persistUser(user: UserSummary | null, scope: AuthSessionScope) {
  if (typeof window === "undefined") return
  const userKey = getUserStorageKey(scope)
  if (user) {
    localStorage.setItem(userKey, JSON.stringify(user))
  } else {
    localStorage.removeItem(userKey)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const scope = useMemo(
    () => getAuthSessionScopeFromPathname(pathname || "/"),
    [pathname]
  )
  const [user, setUser] = useState<UserSummary | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [hydratedScope, setHydratedScope] = useState<AuthSessionScope | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      initAuthFromStorage(scope)
      const { accessToken, refreshToken } = getStoredTokens(scope)
      const storedUser = readStoredUser(scope)

      if (!storedUser || (!accessToken && !refreshToken)) {
        clearTokens(scope)
        persistUser(null, scope)
        setUser(null)
      } else {
        setUser(storedUser)
      }
      setHydratedScope(scope)
      setIsLoading(false)
    }, 0)
    return () => window.clearTimeout(timer)
  }, [scope])

  const setAuth = useCallback(
    (newUser: UserSummary, accessToken: string, refreshToken: string) => {
      storeTokens(accessToken, refreshToken, scope)
      apiClient.setAccessToken(accessToken, scope)
      persistUser(newUser, scope)
      setUser(newUser)
      setHydratedScope(scope)
      setIsLoading(false)
    },
    [scope]
  )

  const clearAuth = useCallback(() => {
    clearTokens(scope)
    apiClient.setAccessToken(null, scope)
    persistUser(null, scope)
    setUser(null)
    setHydratedScope(scope)
    setIsLoading(false)
  }, [scope])

  const refreshUser = useCallback(async () => {
    const { accessToken } = getStoredTokens(scope)
    if (!accessToken) return
    try {
      const me = await apiClient.get<UserSummary>("/api/users/me")
      setUser(me)
      persistUser(me, scope)
    } catch {
      // ignore — token may be invalid; will be handled on next API call
    }
  }, [scope])

  // Keep tabs of the same app area in sync without crossing admin/customer sessions.
  useEffect(() => {
    const userKey = getUserStorageKey(scope)
    function onStorage(e: StorageEvent) {
      if (e.key === userKey) {
        setUser(readStoredUser(scope))
      }
    }
    window.addEventListener("storage", onStorage)
    return () => window.removeEventListener("storage", onStorage)
  }, [scope])

  const scopedUser = hydratedScope === scope ? user : null
  const isSessionLoading = isLoading || hydratedScope !== scope

  return (
    <AuthContext.Provider
      value={{
        user: scopedUser,
        isAuthenticated: !!scopedUser,
        isLoading: isSessionLoading,
        setAuth,
        clearAuth,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error("useAuth must be used inside <AuthProvider>")
  }
  return ctx
}
