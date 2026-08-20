import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-[var(--ring)] focus:ring-offset-2",
  {
    variants: {
      variant: {
        // Booking statuses
        pending:
          "border-yellow-200 bg-yellow-50 text-yellow-800",
        confirmed:
          "border-blue-200 bg-blue-50 text-blue-800",
        checked_in:
          "border-green-200 bg-green-50 text-green-800",
        checked_out:
          "border-gray-200 bg-gray-50 text-gray-800",
        cancelled:
          "border-red-200 bg-red-50 text-red-800",
        no_show:
          "border-orange-200 bg-orange-50 text-orange-800",

        // Room statuses
        available:
          "border-green-200 bg-green-50 text-green-800",
        occupied:
          "border-blue-200 bg-blue-50 text-blue-800",
        dirty:
          "border-orange-200 bg-orange-50 text-orange-800",
        cleaning:
          "border-purple-200 bg-purple-50 text-purple-800",
        maintenance:
          "border-red-200 bg-red-50 text-red-800",

        // Payment statuses
        unpaid:
          "border-yellow-200 bg-yellow-50 text-yellow-800",
        partially_paid:
          "border-blue-200 bg-blue-50 text-blue-800",
        paid:
          "border-green-200 bg-green-50 text-green-800",
        partially_refunded:
          "border-orange-200 bg-orange-50 text-orange-800",
        refunded:
          "border-gray-200 bg-gray-50 text-gray-800",

        // User statuses
        active:
          "border-green-200 bg-green-50 text-green-800",
        suspended:
          "border-red-200 bg-red-50 text-red-800",
        deactivated:
          "border-gray-200 bg-gray-50 text-gray-800",

        // Default
        default:
          "border-[var(--border)] bg-[var(--muted)] text-[var(--foreground)]",
        secondary:
          "border-[var(--border)] bg-[var(--secondary)] text-[var(--secondary-foreground)]",
        success:
          "border-green-200 bg-green-50 text-green-800",
        warning:
          "border-yellow-200 bg-yellow-50 text-yellow-800",
        destructive:
          "border-red-200 bg-red-50 text-red-800",
        outline:
          "border-[var(--border)] text-[var(--foreground)]",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

// Helper function to get booking status variant
export function getBookingStatusVariant(status: string): BadgeProps["variant"] {
  const statusMap: Record<string, BadgeProps["variant"]> = {
    PENDING: "pending",
    CONFIRMED: "confirmed",
    CHECKED_IN: "checked_in",
    CHECKED_OUT: "checked_out",
    CANCELLED: "cancelled",
    NO_SHOW: "no_show",
    EXPIRED: "no_show",
  }
  return statusMap[status] || "default"
}

// Helper function to get payment status variant
export function getPaymentStatusVariant(
  status: string
): BadgeProps["variant"] {
  const statusMap: Record<string, BadgeProps["variant"]> = {
    UNPAID: "unpaid",
    PARTIALLY_PAID: "partially_paid",
    PAID: "paid",
    PARTIALLY_REFUNDED: "partially_refunded",
    REFUNDED: "refunded",
  }
  return statusMap[status] || "default"
}

// Helper function to get user status variant
export function getUserStatusVariant(
  status: string
): BadgeProps["variant"] {
  const statusMap: Record<string, BadgeProps["variant"]> = {
    ACTIVE: "active",
    PENDING_VERIFICATION: "pending",
    SUSPENDED: "suspended",
    DEACTIVATED: "deactivated",
  }
  return statusMap[status] || "default"
}

export { Badge, badgeVariants }
