"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { Plus, RefreshCw, Search, ShieldCheck } from "lucide-react"
import { toast } from "sonner"

import { PolicyFormDialog } from "@/components/admin/cancellation-policies/policy-form-dialog"
import { PolicyTable } from "@/components/admin/cancellation-policies/policy-table"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/lib/auth-context"
import {
  deactivateCancellationPolicy,
  getCancellationPolicies,
} from "@/lib/api/cancellation-policies"
import type { CancellationPolicy } from "@/types/cancellation-policy"

const ALL_STATUSES = "all"
const ACTIVE_STATUS = "active"
const INACTIVE_STATUS = "inactive"

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Đã xảy ra lỗi. Vui lòng thử lại."
}

export default function AdminCancellationPoliciesPage() {
  const router = useRouter()
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [policies, setPolicies] = useState<CancellationPolicy[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState(ALL_STATUSES)
  const [expandedCodes, setExpandedCodes] = useState<Set<string>>(() => new Set())
  const [formOpen, setFormOpen] = useState(false)
  const [editingPolicy, setEditingPolicy] = useState<CancellationPolicy | null>(null)
  const [deactivatingPolicy, setDeactivatingPolicy] = useState<CancellationPolicy | null>(null)
  const [isDeactivating, setIsDeactivating] = useState(false)

  const canManagePolicies = (user?.permissions ?? []).includes("policy:manage")

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/manager/login?redirect=%2Fmanager%2Fcancellation-policies")
    }
  }, [isAuthLoading, isAuthenticated, router])

  const loadData = useCallback(async () => {
    if (!canManagePolicies) {
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setLoadError(null)
    try {
      const data = await getCancellationPolicies()
      setPolicies(data)
      setExpandedCodes((current) => new Set(
        [...current].filter((code) => data.some((policy) => policy.code === code))
      ))
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }, [canManagePolicies])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadData(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadData])

  const filteredPolicies = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("vi")
    return policies.filter((policy) => {
      const matchesSearch = !query
        || `${policy.code} ${policy.name} ${policy.description ?? ""}`
          .toLocaleLowerCase("vi")
          .includes(query)
      const matchesStatus = statusFilter === ALL_STATUSES
        || (statusFilter === ACTIVE_STATUS && policy.isActive)
        || (statusFilter === INACTIVE_STATUS && !policy.isActive)
      return matchesSearch && matchesStatus
    })
  }, [policies, search, statusFilter])

  function toggleExpanded(code: string) {
    setExpandedCodes((current) => {
      const next = new Set(current)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  function openCreateForm() {
    setEditingPolicy(null)
    setFormOpen(true)
  }

  function openEditForm(policy: CancellationPolicy) {
    setEditingPolicy(policy)
    setFormOpen(true)
  }

  async function handleSaved(policy: CancellationPolicy) {
    setExpandedCodes((current) => new Set(current).add(policy.code))
    await loadData()
  }

  async function confirmDeactivate() {
    if (!deactivatingPolicy) return
    setIsDeactivating(true)
    try {
      await deactivateCancellationPolicy(deactivatingPolicy.code)
      toast.success("Đã vô hiệu hóa chính sách hủy")
      setDeactivatingPolicy(null)
      await loadData()
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsDeactivating(false)
    }
  }

  if (isAuthLoading || (!isAuthenticated && !user)) return <PageSkeleton />

  if (!canManagePolicies) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <ShieldCheck className="h-10 w-10 text-[var(--muted-foreground)]" />
          <h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Tài khoản cần permission <code>policy:manage</code> để quản lý chính sách hủy.
          </p>
        </CardContent>
      </Card>
    )
  }

  const activeCount = policies.filter((policy) => policy.isActive).length
  const ruleCount = policies.reduce((total, policy) => total + policy.rules.length, 0)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Chính sách hủy</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Quản lý phí no-show và các bậc hoàn tiền theo thời điểm khách hủy.
          </p>
        </div>
        <Button onClick={openCreateForm}>
          <Plus className="h-4 w-4" /> Tạo policy
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Tổng policy" value={policies.length} />
        <SummaryCard label="Đang hoạt động" value={activeCount} />
        <SummaryCard label="Tổng bậc hoàn tiền" value={ruleCount} />
      </div>

      <Card>
        <CardContent className="grid gap-3 p-4 md:grid-cols-[1fr_220px]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo mã, tên hoặc mô tả..."
              className="pl-9"
            />
          </div>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger><SelectValue placeholder="Trạng thái" /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>Tất cả trạng thái</SelectItem>
              <SelectItem value={ACTIVE_STATUS}>Đang hoạt động</SelectItem>
              <SelectItem value={INACTIVE_STATUS}>Đã vô hiệu hóa</SelectItem>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      {isLoading ? (
        <PageSkeleton tableOnly />
      ) : loadError ? (
        <Card>
          <CardContent className="flex min-h-48 flex-col items-center justify-center gap-3 text-center">
            <p className="text-sm text-[var(--destructive)]">{loadError}</p>
            <Button variant="outline" onClick={() => void loadData()}>
              <RefreshCw className="h-4 w-4" /> Thử lại
            </Button>
          </CardContent>
        </Card>
      ) : (
        <PolicyTable
          policies={filteredPolicies}
          expandedCodes={expandedCodes}
          onToggle={toggleExpanded}
          onEdit={openEditForm}
          onDeactivate={setDeactivatingPolicy}
        />
      )}

      <PolicyFormDialog
        open={formOpen}
        policy={editingPolicy}
        onOpenChange={(open) => {
          setFormOpen(open)
          if (!open) setEditingPolicy(null)
        }}
        onSaved={handleSaved}
      />

      <Dialog
        open={deactivatingPolicy !== null}
        onOpenChange={(open) => !open && !isDeactivating && setDeactivatingPolicy(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Vô hiệu hóa chính sách hủy?</DialogTitle>
            <DialogDescription>
              Policy <strong>{deactivatingPolicy?.name}</strong> ({deactivatingPolicy?.code}) sẽ không còn
              được áp dụng cho booking mới. Booking cũ vẫn giữ nguyên policy snapshot.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" disabled={isDeactivating} onClick={() => setDeactivatingPolicy(null)}>
              Hủy
            </Button>
            <Button variant="destructive" disabled={isDeactivating} onClick={() => void confirmDeactivate()}>
              {isDeactivating ? "Đang xử lý..." : "Vô hiệu hóa"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-[var(--muted-foreground)]">{label}</p>
        <p className="mt-1 text-2xl font-bold">{value}</p>
      </CardContent>
    </Card>
  )
}

function PageSkeleton({ tableOnly = false }: { tableOnly?: boolean }) {
  return (
    <div className="space-y-4">
      {!tableOnly && <Skeleton className="h-16 w-full" />}
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-96 w-full" />
    </div>
  )
}
