"use client"

import { AdminReviewModerationPage } from "@/components/admin/reviews/admin-review-moderation-page"
import { isAdminUser } from "@/lib/admin-auth"
import { useAuth } from "@/lib/auth-context"

export default function ManagerReviewsPage() {
  const { user } = useAuth()
  return <AdminReviewModerationPage mode={isAdminUser(user) ? "admin" : "staff"} />
}
