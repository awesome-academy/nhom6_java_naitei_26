"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import {
  CheckCircle2,
  Eye,
  Loader2,
  RefreshCw,
  Search,
  Undo2,
  WalletCards,
} from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { DataTable } from "@/components/ui/dataTable"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { getManagedPayment, getManagedPayments, requestPaymentRefund, verifyCashPayment } from "@/lib/api/payment-management"
import type {
  PaymentDetail,
  PaymentListItem,
  PaymentMethod,
  PaymentStatus,
  RefundReason,
} from "@/types/payment-management"
import { useAuth } from "@/lib/auth-context"

const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "Chờ thanh toán",
  PROCESSING: "Đang xử lý",
  SUCCEEDED: "Thành công",
  FAILED: "Thất bại",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
  REFUNDED: "Đã hoàn tiền",
  PARTIALLY_REFUNDED: "Hoàn một phần",
}

const METHOD_LABELS: Record<PaymentMethod, string> = {
  INTERNET_BANKING: "Internet Banking",
  CARD: "Thẻ",
  CASH: "Tiền mặt",
  BANK_TRANSFER: "Chuyển khoản",
  E_WALLET: "Ví điện tử",
}

const REFUND_REASON_LABELS: Record<RefundReason, string> = {
  CUSTOMER_CANCEL: "Khách hủy",
  HOTEL_CANCEL: "Khách sạn hủy",
  OVERCHARGE: "Thu dư",
  NO_SHOW_ADJUST: "Điều chỉnh no-show",
  OTHER: "Khác",
}

const EMPTY_FILTERS = {
  booking: "",
  status: "ALL" as PaymentStatus | "ALL",
  method: "ALL" as PaymentMethod | "ALL",
  from: "",
  to: "",
}

function formatMoney(value: number, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value)
}

function formatDateTime(value: string | null) {
  if (!value) return "—"
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

function getErrorMessage(error: unknown, fallback: string) {
  const apiError = error as { message?: string }
  return apiError.message || fallback
}

function getPaymentStatusVariant(status: PaymentStatus) {
  if (status === "SUCCEEDED") return "success" as const
  if (status === "FAILED" || status === "CANCELLED" || status === "EXPIRED") return "destructive" as const
  if (status === "REFUNDED") return "outline" as const
  if (status === "PARTIALLY_REFUNDED") return "warning" as const
  return "pending" as const
}

function isReceived(status: PaymentStatus) {
  return status === "SUCCEEDED" || status === "PARTIALLY_REFUNDED" || status === "REFUNDED"
}

export function PaymentManagementPage() {
  const router = useRouter()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS)
  const [items, setItems] = useState<PaymentListItem[]>([])
  const [pagination, setPagination] = useState({ page: 0, size: 20, totalItems: 0, totalPages: 0 })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedPayment, setSelectedPayment] = useState<PaymentDetail | null>(null)
  const [isDetailOpen, setIsDetailOpen] = useState(false)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [verifyOpen, setVerifyOpen] = useState(false)
  const [refundOpen, setRefundOpen] = useState(false)
  const [providerTxnId, setProviderTxnId] = useState("")
  const [refundAmount, setRefundAmount] = useState("")
  const [refundReason, setRefundReason] = useState<RefundReason>("OTHER")
  const [isActionLoading, setIsActionLoading] = useState(false)

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/admin/login?redirect=%2Fadmin%2Fpayments")
    }
  }, [isAuthLoading, isAuthenticated, router])

  const loadPayments = useCallback(async () => {
    if (!isAuthenticated) return
    setIsLoading(true)
    setError(null)
    try {
      const response = await getManagedPayments({
        booking: appliedFilters.booking || undefined,
        status: appliedFilters.status === "ALL" ? undefined : appliedFilters.status,
        method: appliedFilters.method === "ALL" ? undefined : appliedFilters.method,
        from: appliedFilters.from || undefined,
        to: appliedFilters.to || undefined,
        page: pagination.page,
        size: pagination.size,
      })
      setItems(response.items)
      setPagination({
        page: response.page,
        size: response.size,
        totalItems: response.totalItems,
        totalPages: response.totalPages,
      })
    } catch (loadError) {
      setError(getErrorMessage(loadError, "Không thể tải danh sách thanh toán."))
    } finally {
      setIsLoading(false)
    }
  }, [appliedFilters, isAuthenticated, pagination.page, pagination.size])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadPayments()
    }, 0)

    return () => window.clearTimeout(timer)
  }, [loadPayments])

  async function openDetail(paymentCode: string) {
    setIsDetailOpen(true)
    setIsDetailLoading(true)
    try {
      setSelectedPayment(await getManagedPayment(paymentCode))
    } catch (detailError) {
      setIsDetailOpen(false)
      toast.error(getErrorMessage(detailError, "Không thể tải chi tiết payment."))
    } finally {
      setIsDetailLoading(false)
    }
  }

  function submitFilters() {
    setAppliedFilters(filters)
    setPagination((current) => ({ ...current, page: 0 }))
  }

  function resetFilters() {
    setFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    setPagination((current) => ({ ...current, page: 0 }))
  }

  async function handleVerifyCash() {
    if (!selectedPayment) return
    setIsActionLoading(true)
    try {
      const updated = await verifyCashPayment(selectedPayment.paymentCode, providerTxnId)
      setSelectedPayment(updated)
      setVerifyOpen(false)
      setProviderTxnId("")
      toast.success("Đã xác minh payment tiền mặt")
      await loadPayments()
    } catch (actionError) {
      toast.error(getErrorMessage(actionError, "Không thể xác minh payment."))
    } finally {
      setIsActionLoading(false)
    }
  }

  async function handleRefund() {
    if (!selectedPayment) return
    const amount = Number(refundAmount)
    const available = Math.max(0, selectedPayment.amount - selectedPayment.refundedAmount - (
      selectedPayment.refunds
        .filter((refund) => refund.status === "PENDING" || refund.status === "PROCESSING")
        .reduce((sum, refund) => sum + refund.amount, 0)
    ))
    if (!Number.isFinite(amount) || amount <= 0 || amount > available) {
      toast.error(`Số tiền hoàn phải lớn hơn 0 và không vượt quá ${formatMoney(available, selectedPayment.currency)}.`)
      return
    }

    setIsActionLoading(true)
    try {
      const updated = await requestPaymentRefund(selectedPayment.paymentCode, { amount, reason: refundReason })
      setSelectedPayment(updated)
      setRefundOpen(false)
      setRefundAmount("")
      toast.success("Đã tạo yêu cầu hoàn tiền, chờ phê duyệt")
      await loadPayments()
    } catch (actionError) {
      toast.error(getErrorMessage(actionError, "Không thể tạo yêu cầu hoàn tiền."))
    } finally {
      setIsActionLoading(false)
    }
  }

  const availableRefund = useMemo(() => {
    if (!selectedPayment) return 0
    const pending = selectedPayment.refunds
      .filter((refund) => refund.status === "PENDING" || refund.status === "PROCESSING")
      .reduce((sum, refund) => sum + refund.amount, 0)
    return Math.max(0, selectedPayment.amount - selectedPayment.refundedAmount - pending)
  }, [selectedPayment])

  const columns = [
    {
      key: "paymentCode",
      header: "Payment",
      render: (item: PaymentListItem) => (
        <div className="flex flex-col gap-1">
          <span className="font-mono text-sm font-medium">{item.paymentCode}</span>
          <span className="text-xs text-muted-foreground">{formatDateTime(item.createdAt)}</span>
        </div>
      ),
    },
    {
      key: "bookingCode",
      header: "Booking",
      render: (item: PaymentListItem) => (
        <div className="flex flex-col gap-1">
          <span className="font-medium">{item.bookingCode}</span>
          <span className="text-xs text-muted-foreground">{item.contactName}</span>
        </div>
      ),
    },
    {
      key: "method",
      header: "Phương thức",
      render: (item: PaymentListItem) => METHOD_LABELS[item.method],
    },
    {
      key: "amount",
      header: "Số tiền",
      className: "text-right font-medium",
      render: (item: PaymentListItem) => formatMoney(item.amount, item.currency),
    },
    {
      key: "status",
      header: "Trạng thái",
      render: (item: PaymentListItem) => (
        <Badge variant={getPaymentStatusVariant(item.status)}>{STATUS_LABELS[item.status]}</Badge>
      ),
    },
    {
      key: "provider",
      header: "Gateway",
      render: (item: PaymentListItem) => item.providerTxnId || item.provider || "—",
    },
    {
      key: "actions",
      header: "",
      className: "text-right",
      render: (item: PaymentListItem) => (
        <Button variant="ghost" size="sm" onClick={() => void openDetail(item.paymentCode)}>
          <Eye data-icon="inline-start" /> Chi tiết
        </Button>
      ),
    },
  ]

  if (isAuthLoading || !isAuthenticated) {
    return <div className="flex min-h-64 items-center justify-center"><Loader2 className="animate-spin" /></div>
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">Quản lý thanh toán</h1>
          <p className="text-sm text-muted-foreground">Theo dõi payment, xác minh tiền mặt và tạo yêu cầu hoàn tiền.</p>
        </div>
        <Button variant="outline" onClick={() => void loadPayments()} disabled={isLoading}>
          <RefreshCw data-icon="inline-start" className={isLoading ? "animate-spin" : undefined} /> Làm mới
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>Không thể tải dữ liệu</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Bộ lọc payment</CardTitle>
          <CardDescription>Tìm theo mã booking/payment, trạng thái, phương thức và ngày tạo.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
            <div className="flex flex-col gap-2 lg:col-span-2">
              <Label htmlFor="payment-booking-search">Booking hoặc payment</Label>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="payment-booking-search"
                  className="pl-9"
                  placeholder="BK-..., tên khách..."
                  value={filters.booking}
                  onChange={(event) => setFilters((current) => ({ ...current, booking: event.target.value }))}
                  onKeyDown={(event) => { if (event.key === "Enter") submitFilters() }}
                />
              </div>
            </div>
            <FilterSelect
              label="Trạng thái"
              value={filters.status}
              onChange={(value) => setFilters((current) => ({ ...current, status: value as typeof current.status }))}
              options={["ALL", "PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED", "PARTIALLY_REFUNDED", "REFUNDED"]}
              labels={{ ALL: "Tất cả trạng thái", ...STATUS_LABELS }}
            />
            <FilterSelect
              label="Phương thức"
              value={filters.method}
              onChange={(value) => setFilters((current) => ({ ...current, method: value as typeof current.method }))}
              options={["ALL", "INTERNET_BANKING", "CARD", "CASH", "BANK_TRANSFER", "E_WALLET"]}
              labels={{ ALL: "Tất cả phương thức", ...METHOD_LABELS }}
            />
            <div className="flex flex-col gap-2">
              <Label htmlFor="payment-from">Từ ngày</Label>
              <Input id="payment-from" type="date" value={filters.from} onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))} />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="payment-to">Đến ngày</Label>
              <Input id="payment-to" type="date" value={filters.to} onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))} />
            </div>
          </div>
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="ghost" onClick={resetFilters}>Xóa lọc</Button>
            <Button onClick={submitFilters}><Search data-icon="inline-start" /> Áp dụng</Button>
          </div>
        </CardContent>
      </Card>

      <DataTable
        columns={columns}
        data={items}
        keyExtractor={(item) => item.paymentCode}
        onRowClick={(item) => void openDetail(item.paymentCode)}
        isLoading={isLoading}
        emptyMessage="Không tìm thấy payment nào"
        pagination={{
          page: pagination.page,
          size: pagination.size,
          totalItems: pagination.totalItems,
          totalPages: pagination.totalPages,
          onPageChange: (page) => setPagination((current) => ({ ...current, page })),
        }}
      />

      <Sheet open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <SheetContent className="w-full overflow-y-auto p-6 sm:max-w-2xl">
          {isDetailLoading || !selectedPayment ? (
            <div className="flex min-h-64 items-center justify-center"><Loader2 className="animate-spin" /></div>
          ) : (
            <div className="flex flex-col gap-6">
              <SheetHeader className="flex flex-col gap-1 pr-8">
                <SheetTitle className="flex items-center gap-2"><WalletCards /> {selectedPayment.paymentCode}</SheetTitle>
                <SheetDescription>{selectedPayment.bookingCode} · {selectedPayment.contactName}</SheetDescription>
              </SheetHeader>

              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={getPaymentStatusVariant(selectedPayment.status)}>{STATUS_LABELS[selectedPayment.status]}</Badge>
                <Badge variant="outline">{METHOD_LABELS[selectedPayment.method]}</Badge>
                {selectedPayment.provider && <Badge variant="outline">{selectedPayment.provider}</Badge>}
              </div>

              <Card>
                <CardHeader><CardTitle className="text-base">Thông tin giao dịch</CardTitle></CardHeader>
                <CardContent className="grid gap-4 sm:grid-cols-2">
                  <DetailItem label="Số tiền" value={formatMoney(selectedPayment.amount, selectedPayment.currency)} />
                  <DetailItem label="Đã hoàn" value={formatMoney(selectedPayment.refundedAmount, selectedPayment.currency)} />
                  <DetailItem label="Gateway reference" value={selectedPayment.providerTxnId || "Chưa có"} mono />
                  <DetailItem label="Ngân hàng" value={selectedPayment.providerBankCode || "—"} />
                  <DetailItem label="Thanh toán lúc" value={formatDateTime(selectedPayment.paidAt)} />
                  <DetailItem label="Xác minh lúc" value={formatDateTime(selectedPayment.verifiedAt)} />
                  <DetailItem label="Tạo lúc" value={formatDateTime(selectedPayment.createdAt)} />
                  <DetailItem label="Hết hạn" value={formatDateTime(selectedPayment.expiresAt)} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader><CardTitle className="text-base">Tóm tắt booking</CardTitle></CardHeader>
                <CardContent className="grid gap-4 sm:grid-cols-2">
                  <DetailItem label="Trạng thái booking" value={selectedPayment.bookingStatus} />
                  <DetailItem label="Trạng thái thanh toán" value={selectedPayment.bookingPaymentStatus} />
                  <DetailItem label="Tổng booking" value={formatMoney(selectedPayment.bookingTotalAmount, selectedPayment.currency)} />
                  <DetailItem label="Đã thanh toán" value={formatMoney(selectedPayment.bookingPaidAmount, selectedPayment.currency)} />
                </CardContent>
              </Card>

              {selectedPayment.refunds.length > 0 && (
                <Card>
                  <CardHeader><CardTitle className="text-base">Lịch sử hoàn tiền</CardTitle></CardHeader>
                  <CardContent className="flex flex-col gap-3">
                    {selectedPayment.refunds.map((refund) => (
                      <div key={refund.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md border p-3 text-sm">
                        <div className="flex flex-col gap-1">
                          <span className="font-medium">{formatMoney(refund.amount, selectedPayment.currency)}</span>
                          <span className="text-muted-foreground">{REFUND_REASON_LABELS[refund.reason]} · {formatDateTime(refund.createdAt)}</span>
                        </div>
                        <Badge variant={refund.status === "COMPLETED" ? "success" : refund.status === "REJECTED" || refund.status === "FAILED" ? "destructive" : "warning"}>{refund.status}</Badge>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              )}

              <div className="flex flex-wrap gap-2">
                {selectedPayment.method === "CASH" && (selectedPayment.status === "PENDING" || selectedPayment.status === "PROCESSING") && (
                  <Button onClick={() => setVerifyOpen(true)}><CheckCircle2 data-icon="inline-start" /> Xác minh tiền mặt</Button>
                )}
                {isReceived(selectedPayment.status) && availableRefund > 0 && (
                  <Button variant="outline" onClick={() => { setRefundAmount(String(availableRefund)); setRefundOpen(true) }}>
                    <Undo2 data-icon="inline-start" /> Hoàn tiền
                  </Button>
                )}
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>

      <Dialog open={verifyOpen} onOpenChange={setVerifyOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Xác minh payment tiền mặt</DialogTitle>
            <DialogDescription>Thao tác này đánh dấu payment là thành công và đồng bộ ledger booking.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="cash-reference">Mã giao dịch nội bộ (không bắt buộc)</Label>
            <Input id="cash-reference" value={providerTxnId} maxLength={120} onChange={(event) => setProviderTxnId(event.target.value)} placeholder="Mặc định: CASH-{selectedPayment?.paymentCode}" />
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setVerifyOpen(false)}>Hủy</Button>
            <Button onClick={() => void handleVerifyCash()} disabled={isActionLoading}>
              {isActionLoading && <Loader2 data-icon="inline-start" className="animate-spin" />} Xác minh
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={refundOpen} onOpenChange={setRefundOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Tạo yêu cầu hoàn tiền</DialogTitle>
            <DialogDescription>Yêu cầu sẽ ở trạng thái PENDING để Admin phê duyệt theo workflow refund.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="refund-amount">Số tiền hoàn (tối đa {selectedPayment ? formatMoney(availableRefund, selectedPayment.currency) : "—"})</Label>
              <Input id="refund-amount" type="number" min="0.01" step="0.01" value={refundAmount} onChange={(event) => setRefundAmount(event.target.value)} />
            </div>
            <div className="flex flex-col gap-2">
              <Label>Lý do</Label>
              <Select value={refundReason} onValueChange={(value) => setRefundReason(value as RefundReason)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {(Object.keys(REFUND_REASON_LABELS) as RefundReason[]).map((reason) => (
                    <SelectItem key={reason} value={reason}>{REFUND_REASON_LABELS[reason]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setRefundOpen(false)}>Hủy</Button>
            <Button onClick={() => void handleRefund()} disabled={isActionLoading}>
              {isActionLoading && <Loader2 data-icon="inline-start" className="animate-spin" />} Gửi yêu cầu
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
  labels,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: string[]
  labels: Record<string, string>
}) {
  return (
    <div className="flex flex-col gap-2">
      <Label>{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger><SelectValue /></SelectTrigger>
        <SelectContent>
          {options.map((option) => <SelectItem key={option} value={option}>{labels[option] || option}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  )
}

function DetailItem({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className={mono ? "break-all font-mono text-sm" : "text-sm font-medium"}>{value}</span>
    </div>
  )
}
