import { AuthLayoutNew } from "@/components/auth/auth-layout-new"

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return <AuthLayoutNew>{children}</AuthLayoutNew>
}
