"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { Eye, Loader2, Search, UserRound, UserRoundCheck, UserRoundX } from "lucide-react"
import { toast } from "sonner"

import { Badge, getUserStatusVariant } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { DataTable } from "@/components/ui/dataTable"
import { Input } from "@/components/ui/input"
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { getCustomers, updateCustomerStatus } from "@/lib/api/admin-customers"
import type { CustomerAccountStatus, CustomerListItem, CustomerStatus } from "@/types/admin-customer"

const ALL_STATUS = "ALL" as const
const statusLabels: Record<CustomerStatus | typeof ALL_STATUS, string> = {
  ALL: "Tất cả trạng thái",
  ACTIVE: "Đang hoạt động",
  DEACTIVATED: "Đã vô hiệu hóa",
  PENDING_VERIFICATION: "Chờ xác thực",
  SUSPENDED: "Đang tạm khóa",
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(value))
}

function getErrorMessage(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : "Không thể thực hiện thao tác. Vui lòng thử lại."
}

function getPageNumbers(currentPage: number, totalPages: number) {
  if (totalPages <= 5) return Array.from({ length: totalPages }, (_, index) => index)
  if (currentPage <= 2) return [0, 1, 2, -1, totalPages - 1]
  if (currentPage >= totalPages - 3) return [0, -1, totalPages - 3, totalPages - 2, totalPages - 1]
  return [0, -1, currentPage, -1, totalPages - 1]
}

export function CustomerList() {
  const router = useRouter()
  const [customers, setCustomers] = useState<CustomerListItem[]>([])
  const [searchInput, setSearchInput] = useState("")
  const [search, setSearch] = useState("")
  const [status, setStatus] = useState<CustomerStatus | typeof ALL_STATUS>(ALL_STATUS)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [pendingStatus, setPendingStatus] = useState<{
    customer: CustomerListItem
    status: CustomerAccountStatus
  } | null>(null)
  const [updatingId, setUpdatingId] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSearch(searchInput.trim())
      setPage(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [searchInput])

  const loadCustomers = useCallback(async () => {
    setIsLoading(true)
    setLoadError(null)
    try {
      const response = await getCustomers({ page, search, status })
      setCustomers(Array.isArray(response.items) ? response.items : [])
      setPage(response.page)
      setTotalPages(response.totalPages)
      setTotalItems(response.totalItems)
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }, [page, search, status])

  useEffect(() => {
    const timer = window.setTimeout(() => void loadCustomers(), 0)
    return () => window.clearTimeout(timer)
  }, [loadCustomers])

  async function applyStatusChange(customer: CustomerListItem, nextStatus: CustomerAccountStatus) {
    setUpdatingId(customer.publicId)
    try {
      await updateCustomerStatus(customer.publicId, nextStatus)
      setCustomers((current) => current.map((item) => (
        item.publicId === customer.publicId ? { ...item, status: nextStatus } : item
      )))
      toast.success(nextStatus === "ACTIVE" ? "Đã kích hoạt lại tài khoản" : "Đã vô hiệu hóa tài khoản")
      setPendingStatus(null)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setUpdatingId(null)
    }
  }

  const columns = useMemo(() => [
    {
      key: "fullName",
      header: "Họ tên",
      render: (customer: CustomerListItem) => (
        <div className="flex items-center gap-3">
          <div className="flex size-9 items-center justify-center rounded-full bg-muted text-muted-foreground">
            <UserRound />
          </div>
          <div className="flex min-w-0 flex-col gap-0.5">
            <button
              type="button"
              className="truncate text-left font-medium text-foreground hover:text-accent"
              onClick={(event) => {
                event.stopPropagation()
                router.push(`/manager/guests/${customer.publicId}`)
              }}
            >
              {customer.fullName}
            </button>
            <span className="text-xs text-muted-foreground">Customer</span>
          </div>
        </div>
      ),
    },
    { key: "email", header: "Email" },
    {
      key: "phone",
      header: "Số điện thoại",
      render: (customer: CustomerListItem) => customer.phone || "—",
    },
    {
      key: "status",
      header: "Trạng thái",
      render: (customer: CustomerListItem) => (
        <Badge variant={getUserStatusVariant(customer.status)}>
          {statusLabels[customer.status]}
        </Badge>
      ),
    },
    {
      key: "bookingCount",
      header: "Số booking",
      render: (customer: CustomerListItem) => customer.bookingCount ?? 0,
    },
    {
      key: "createdAt",
      header: "Ngày đăng ký",
      render: (customer: CustomerListItem) => formatDate(customer.createdAt),
    },
    {
      key: "actions",
      header: "Thao tác",
      className: "text-right",
      render: (customer: CustomerListItem) => {
        const isUpdating = updatingId === customer.publicId
        return (
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-label={`Xem ${customer.fullName}`}
              onClick={(event) => {
                event.stopPropagation()
                router.push(`/manager/guests/${customer.publicId}`)
              }}
            >
              <Eye data-icon="inline-start" />
              Xem
            </Button>
            {customer.status === "ACTIVE" && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isUpdating}
                onClick={(event) => {
                  event.stopPropagation()
                  setPendingStatus({ customer, status: "DEACTIVATED" })
                }}
              >
                {isUpdating ? <Loader2 className="animate-spin" /> : <UserRoundX />}
                Vô hiệu hóa
              </Button>
            )}
            {customer.status === "DEACTIVATED" && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isUpdating}
                onClick={(event) => {
                  event.stopPropagation()
                  void applyStatusChange(customer, "ACTIVE")
                }}
              >
                {isUpdating ? <Loader2 className="animate-spin" /> : <UserRoundCheck />}
                Kích hoạt lại
              </Button>
            )}
          </div>
        )
      },
    },
  ], [router, updatingId])

  const pageNumbers = getPageNumbers(page, totalPages)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight">Khách hàng</h1>
        <p className="text-sm text-muted-foreground">
          Quản lý tài khoản customer và xem lịch sử booking ở chế độ chỉ đọc.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Danh sách tài khoản</CardTitle>
          <CardDescription>
            {totalItems} customer · 20 tài khoản mỗi trang
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-3 md:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Tìm theo họ tên, email hoặc số điện thoại..."
                className="pl-10"
              />
            </div>
            <Select
              value={status}
              onValueChange={(value) => {
                setStatus(value as CustomerStatus | typeof ALL_STATUS)
                setPage(0)
              }}
            >
              <SelectTrigger className="w-full md:w-56">
                <SelectValue placeholder="Trạng thái" />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(statusLabels) as Array<CustomerStatus | typeof ALL_STATUS>).map((value) => (
                  <SelectItem key={value} value={value}>
                    {statusLabels[value]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {isLoading ? (
            <Skeleton className="h-96 w-full" />
          ) : loadError ? (
            <div className="flex flex-col items-center gap-3 py-12 text-center">
              <p className="text-sm text-destructive">{loadError}</p>
              <Button variant="outline" onClick={() => void loadCustomers()}>Thử lại</Button>
            </div>
          ) : (
            <DataTable
              columns={columns}
              data={customers}
              keyExtractor={(customer) => customer.publicId}
              onRowClick={(customer) => router.push(`/manager/guests/${customer.publicId}`)}
              emptyMessage="Không tìm thấy tài khoản customer"
            />
          )}

          {!isLoading && !loadError && totalPages > 1 && (
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    aria-disabled={page === 0}
                    className={page === 0 ? "pointer-events-none opacity-50" : undefined}
                    onClick={(event) => {
                      event.preventDefault()
                      if (page > 0) setPage(page - 1)
                    }}
                  />
                </PaginationItem>
                {pageNumbers.map((pageNumber, index) => (
                  <PaginationItem key={`${pageNumber}-${index}`}>
                    {pageNumber < 0 ? (
                      <PaginationEllipsis />
                    ) : (
                      <PaginationLink
                        href="#"
                        isActive={pageNumber === page}
                        onClick={(event) => {
                          event.preventDefault()
                          setPage(pageNumber)
                        }}
                      >
                        {pageNumber + 1}
                      </PaginationLink>
                    )}
                  </PaginationItem>
                ))}
                <PaginationItem>
                  <PaginationNext
                    href="#"
                    aria-disabled={page >= totalPages - 1}
                    className={page >= totalPages - 1 ? "pointer-events-none opacity-50" : undefined}
                    onClick={(event) => {
                      event.preventDefault()
                      if (page < totalPages - 1) setPage(page + 1)
                    }}
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          )}
        </CardContent>
      </Card>

      <Dialog
        open={pendingStatus !== null}
        onOpenChange={(open) => {
          if (!open && !updatingId) setPendingStatus(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Vô hiệu hóa tài khoản?</DialogTitle>
            <DialogDescription>
              {pendingStatus?.customer.fullName} sẽ không thể đăng nhập hoặc dùng API customer.
              Booking và lịch sử của khách vẫn được giữ nguyên.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" disabled={updatingId !== null} onClick={() => setPendingStatus(null)}>
              Hủy
            </Button>
            <Button
              variant="destructive"
              disabled={updatingId !== null || pendingStatus === null}
              onClick={() => {
                if (pendingStatus) void applyStatusChange(pendingStatus.customer, pendingStatus.status)
              }}
            >
              {updatingId !== null ? <Loader2 className="animate-spin" /> : <UserRoundX />}
              Vô hiệu hóa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
