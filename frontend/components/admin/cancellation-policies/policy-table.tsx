"use client"

import { Fragment } from "react"
import { ChevronDown, ChevronRight, Pencil, Power } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { CancellationPolicy } from "@/types/cancellation-policy"

interface PolicyTableProps {
  policies: CancellationPolicy[]
  expandedCodes: Set<string>
  onToggle: (code: string) => void
  onEdit: (policy: CancellationPolicy) => void
  onDeactivate: (policy: CancellationPolicy) => void
}

function formatPercent(value: number): string {
  return new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(value)
}

export function PolicyTable({
  policies,
  expandedCodes,
  onToggle,
  onEdit,
  onDeactivate,
}: PolicyTableProps) {
  return (
    <div className="overflow-hidden rounded-xl border border-[var(--border)] bg-[var(--card)]">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-[var(--border)] bg-[var(--muted)]/50">
              <th className="w-12 px-4 py-3"><span className="sr-only">Mở rộng</span></th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Policy</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">No-show</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Tăng giá</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Bậc hoàn tiền</th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Trạng thái</th>
              <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {policies.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-[var(--muted-foreground)]">
                  Không có cancellation policy phù hợp.
                </td>
              </tr>
            ) : policies.map((policy) => {
              const expanded = expandedCodes.has(policy.code)
              return (
                <Fragment key={policy.code}>
                  <tr className="transition-colors hover:bg-[var(--muted)]/30">
                    <td className="px-4 py-3">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        aria-label={expanded ? `Thu gọn ${policy.name}` : `Mở rộng ${policy.name}`}
                        aria-expanded={expanded}
                        onClick={() => onToggle(policy.code)}
                      >
                        {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                      </Button>
                    </td>
                    <td className="min-w-56 px-4 py-3 text-sm">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-medium">{policy.name}</p>
                        {policy.isDefault && <Badge variant="secondary">Mặc định</Badge>}
                      </div>
                      <p className="mt-1 font-mono text-xs text-[var(--muted-foreground)]">{policy.code}</p>
                    </td>
                    <td className="px-4 py-3 text-sm font-medium">{formatPercent(policy.noShowChargePercent)}%</td>
                    <td className="px-4 py-3 text-sm font-medium">{formatPercent(policy.priceAdjustmentPercent)}%</td>
                    <td className="px-4 py-3 text-sm">{policy.rules.length} bậc</td>
                    <td className="px-4 py-3 text-sm">
                      <Badge variant={policy.isActive ? "active" : "deactivated"}>
                        {policy.isActive ? "Hoạt động" : "Vô hiệu hóa"}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <Button variant="outline" size="sm" onClick={() => onEdit(policy)}>
                          <Pencil className="h-4 w-4" /> Sửa
                        </Button>
                        {policy.isActive && (
                          <Button variant="destructive" size="sm" onClick={() => onDeactivate(policy)}>
                            <Power className="h-4 w-4" /> Vô hiệu hóa
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>

                  {expanded && (
                    <tr className="bg-[var(--muted)]/15">
                      <td colSpan={7} className="px-6 py-5">
                        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(360px,1.3fr)]">
                          <div>
                            <p className="text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Mô tả</p>
                            <p className="mt-2 text-sm leading-6">
                              {policy.description || "Chưa có mô tả cho chính sách này."}
                            </p>
                          </div>
                          <div className="overflow-hidden rounded-lg border bg-[var(--card)]">
                            <table className="w-full">
                              <thead>
                                <tr className="border-b bg-[var(--muted)]/40">
                                  <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Thời điểm hủy</th>
                                  <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-[var(--muted-foreground)]">Mức hoàn</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-[var(--border)]">
                                {[...policy.rules]
                                  .sort((left, right) => right.minHoursBefore - left.minHoursBefore)
                                  .map((rule) => (
                                    <tr key={rule.minHoursBefore}>
                                      <td className="px-4 py-2.5 text-sm">Trước ít nhất {rule.minHoursBefore} giờ</td>
                                      <td className="px-4 py-2.5 text-right text-sm font-semibold">{formatPercent(rule.refundPercent)}%</td>
                                    </tr>
                                  ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
