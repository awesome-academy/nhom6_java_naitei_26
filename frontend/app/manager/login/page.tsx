"use client"

import { Suspense } from "react"
import { Loader2 } from "lucide-react"

import { AdminLoginForm } from "@/components/auth/admin-login-form"

function AdminLoginLoading() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background">
      <Loader2 className="animate-spin text-primary" />
    </main>
  )
}

export default function ManagerLoginPage() {
  return (
    <Suspense fallback={<AdminLoginLoading />}>
      <AdminLoginForm />
    </Suspense>
  )
}
