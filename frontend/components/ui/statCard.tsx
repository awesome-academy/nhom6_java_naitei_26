import * as React from "react"
import { cn } from "@/lib/utils"

interface StatCardProps extends React.HTMLAttributes<HTMLDivElement> {
  label: string
  value: string | number
  icon?: React.ReactNode
  trend?: {
    value: number
    isPositive: boolean
  }
  description?: string
}

export function StatCard({
  label,
  value,
  icon,
  trend,
  description,
  className,
  ...props
}: StatCardProps) {
  return (
    <div
      className={cn(
        "rounded-xl border border-[var(--border)] bg-[var(--card)] p-6 shadow-sm",
        className
      )}
      {...props}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-2">
          <p className="text-sm font-medium text-[var(--muted-foreground)]">
            {label}
          </p>
          <p className="text-3xl font-bold tracking-tight text-[var(--foreground)]">
            {value}
          </p>
          {description && (
            <p className="text-sm text-[var(--muted-foreground)]">
              {description}
            </p>
          )}
          {trend && (
            <div className="flex items-center gap-1">
              <span
                className={cn(
                  "text-sm font-medium",
                  trend.isPositive
                    ? "text-[var(--success)]"
                    : "text-[var(--destructive)]"
                )}
              >
                {trend.isPositive ? "+" : "-"}
                {Math.abs(trend.value)}%
              </span>
              <span className="text-sm text-[var(--muted-foreground)]">
                so với tuần trước
              </span>
            </div>
          )}
        </div>
        {icon && (
          <div className="rounded-lg bg-[var(--accent)]/10 p-3 text-[var(--accent)]">
            {icon}
          </div>
        )}
      </div>
    </div>
  )
}
