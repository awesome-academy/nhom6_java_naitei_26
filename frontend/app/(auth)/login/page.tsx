"use client"

import { Suspense } from "react"
import { LoginFormNew } from "@/components/auth/login-form-new"
import { Loader2 } from "lucide-react"

function LoginLoading() {
  return (
    <div className="flex flex-col items-center justify-center py-12">
      <Loader2 className="h-8 w-8 animate-spin text-[var(--accent)]" />
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={<LoginLoading />}>
      <LoginFormNew />
    </Suspense>
  )
}
