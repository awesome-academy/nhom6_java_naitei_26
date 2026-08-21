"use client"

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react"
import type { UserSummary } from "@/types/auth"
import {
  clearTokens,
  getStoredTokens,
  initAuthFromStorage,
  storeTokens,
} from "@/lib/api/auth"
import { apiClient } from "@/lib/api/client"

interface AuthContextValue {
  user: UserSummary | null
  isAuthenticated: boolean
  isLoading: boolean
  setAuth: (user: UserSummary, accessToken: string, refreshToken: string) => void
  clearAuth: () => void
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

const USER_KEY = "auth_user"

function readStoredUser(): UserSummary | null {
  if (typeof window === "undefined") return null
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserSummary
  } catch {
    return null
  }
}

function persistUser(user: UserSummary | null) {
  if (typeof window === "undefined") return
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      initAuthFromStorage()
      setUser(readStoredUser())
      setIsLoading(false)
    }, 0)
    return () => window.clearTimeout(timer)
  }, [])

  const setAuth = useCallback(
    (newUser: UserSummary, accessToken: string, refreshToken: string) => {
      storeTokens(accessToken, refreshToken)
      persistUser(newUser)
      setUser(newUser)
    },
    []
  )

  const clearAuth = useCallback(() => {
    clearTokens()
    persistUser(null)
    setUser(null)
  }, [])

  const refreshUser = useCallback(async () => {
    const { accessToken } = getStoredTokens()
    if (!accessToken) return
    try {
      const me = await apiClient.get<UserSummary>("/api/users/me")
      setUser(me)
      persistUser(me)
    } catch {
      // ignore — token may be invalid; will be handled on next API call
    }
  }, [])

  // Listen for storage events so logout in one tab affects the other
  useEffect(() => {
    function onStorage(e: StorageEvent) {
      if (e.key === USER_KEY) {
        setUser(readStoredUser())
      }
    }
    window.addEventListener("storage", onStorage)
    return () => window.removeEventListener("storage", onStorage)
  }, [])

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
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
