"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { BedDouble, ImageIcon, Plus, RefreshCw, Search } from "lucide-react"
import { toast } from "sonner"

import { RoomTypeFormDialog } from "@/components/admin/room-types/room-type-form-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { DataTable } from "@/components/ui/dataTable"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/lib/auth-context"
import { deleteRoomType, getAmenities, getRoomTypeStats, getRoomTypes } from "@/lib/api/room-types"
import type { Amenity, RoomType, RoomTypeStats } from "@/types/room-type"

const PAGE_SIZE = 10

function formatPrice(value: number, currency: string): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value)
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Đã xảy ra lỗi. Vui lòng thử lại."
}

export default function AdminRoomTypesPage() {
  const router = useRouter()
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [amenities, setAmenities] = useState<Amenity[]>([])
  const [stats, setStats] = useState<RoomTypeStats>({ total: 0, active: 0, deactivated: 0 })
  const [search, setSearch] = useState("")
  const [page, setPage] = useState(1)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editingRoomType, setEditingRoomType] = useState<RoomType | null>(null)
  const [deletingRoomType, setDeletingRoomType] = useState<RoomType | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const permissions = user?.permissions ?? []
  const canRead = permissions.includes("room:read")
  const canCreate = permissions.includes("room:create")
  const canUpdate = permissions.includes("room:update")
  const canDelete = permissions.includes("room:delete")

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.replace("/login?redirect=%2Fadmin%2Froom-types")
    }
  }, [isAuthLoading, isAuthenticated, router])

  const loadData = useCallback(async () => {
    if (!canRead) {
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setLoadError(null)
    try {
      const [roomTypeData, amenityData, statsData] = await Promise.all([
        getRoomTypes(),
        getAmenities(),
        getRoomTypeStats(),
      ])
      setRoomTypes(roomTypeData)
      setAmenities(amenityData)
      setStats(statsData)
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }, [canRead])

  useEffect(() => {
    if (isAuthLoading || !isAuthenticated) return
    const timer = window.setTimeout(() => void loadData(), 0)
    return () => window.clearTimeout(timer)
  }, [isAuthLoading, isAuthenticated, loadData])

  const filteredRoomTypes = useMemo(() => {
    const normalizedSearch = search.trim().toLocaleLowerCase("vi")
    if (!normalizedSearch) return roomTypes
    return roomTypes.filter(
      (roomType) =>
        roomType.code.toLocaleLowerCase("vi").includes(normalizedSearch) ||
        roomType.name.toLocaleLowerCase("vi").includes(normalizedSearch)
    )
  }, [roomTypes, search])

  const totalPages = Math.max(1, Math.ceil(filteredRoomTypes.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pagedRoomTypes = filteredRoomTypes.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE
  )

  const columns = [
    {
      key: "name",
      header: "Loại phòng",
      render: (roomType: RoomType) => {
        const primaryImage = roomType.images.find((image) => image.isPrimary) ?? roomType.images[0]
        return (
          <div className="flex min-w-56 items-center gap-3">
            <div className="flex h-12 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[var(--muted)]">
              {primaryImage ? (
                <div
                  role="img"
                  aria-label={primaryImage.altText}
                  className="h-full w-full bg-cover bg-center"
                  style={{ backgroundImage: `url("${primaryImage.downloadUrl}")` }}
                />
              ) : (
                <ImageIcon className="h-5 w-5 text-[var(--muted-foreground)]" />
              )}
            </div>
            <div>
              <p className="font-medium">{roomType.name}</p>
              <p className="text-xs text-[var(--muted-foreground)]">{roomType.code}</p>
            </div>
          </div>
        )
      },
    },
    {
      key: "beds",
      header: "Giường",
      render: (roomType: RoomType) => (
        <div className="min-w-32">
          <p>{roomType.bedCount} giường</p>
          <p className="text-xs text-[var(--muted-foreground)]">
            {roomType.beds.map((bed) => `${bed.quantity} ${bed.bedType.replace("_", " ")}`).join(", ")}
          </p>
        </div>
      ),
    },
    {
      key: "maxOccupancy",
      header: "Sức chứa",
      render: (roomType: RoomType) => (
        <span>{roomType.maxOccupancy} khách</span>
      ),
    },
    {
      key: "sizeSqm",
      header: "Diện tích",
      render: (roomType: RoomType) => (
        <span className={roomType.sizeSqm === null ? "text-[var(--muted-foreground)]" : undefined}>
          {roomType.sizeSqm === null ? "Chưa cập nhật" : `${roomType.sizeSqm} m²`}
        </span>
      ),
    },
    {
      key: "amenities",
      header: "Tiện nghi",
      render: (roomType: RoomType) => (
        <div className="flex min-w-40 flex-wrap gap-1">
          {roomType.amenities.slice(0, 3).map((amenity) => (
            <Badge key={amenity.code} variant="secondary">{amenity.name}</Badge>
          ))}
          {roomType.amenities.length > 3 && (
            <Badge variant="outline">+{roomType.amenities.length - 3}</Badge>
          )}
          {roomType.amenities.length === 0 && <span className="text-[var(--muted-foreground)]">—</span>}
        </div>
      ),
    },
    {
      key: "basePrice",
      header: "Giá/đêm",
      className: "text-right",
      render: (roomType: RoomType) => (
        <span className="font-medium">{formatPrice(roomType.basePrice, roomType.currency)}</span>
      ),
    },
    {
      key: "isActive",
      header: "Trạng thái",
      render: (roomType: RoomType) => (
        <Badge variant={roomType.isActive ? "active" : "deactivated"}>
          {roomType.isActive ? "Hoạt động" : "Vô hiệu hóa"}
        </Badge>
      ),
    },
  ]

  const actions = [
    ...(canUpdate
      ? [{ label: "Sửa", onClick: (roomType: RoomType) => {
        setEditingRoomType(roomType)
        setFormOpen(true)
      } }]
      : []),
    ...(canDelete
      ? [{ label: "Xóa", variant: "destructive" as const, onClick: setDeletingRoomType }]
      : []),
  ]

  async function confirmDelete() {
    if (!deletingRoomType) return
    setIsDeleting(true)
    try {
      await deleteRoomType(deletingRoomType.code)
      setDeletingRoomType(null)
      toast.success("Đã xóa loại phòng")
      await loadData()
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsDeleting(false)
    }
  }

  if (isAuthLoading || (!isAuthenticated && !user)) {
    return <PageSkeleton />
  }

  if (!canRead) {
    return (
      <Card>
        <CardContent className="flex min-h-64 flex-col items-center justify-center gap-3 text-center">
          <BedDouble className="h-10 w-10 text-[var(--muted-foreground)]" />
          <h1 className="text-xl font-semibold">Không có quyền truy cập</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Tài khoản cần permission <code>room:read</code> để xem loại phòng.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Quản lý loại phòng</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Cấu hình loại phòng, giường, tiện nghi, giá và hình ảnh.
          </p>
        </div>
        {canCreate && (
          <Button onClick={() => {
            setEditingRoomType(null)
            setFormOpen(true)
          }}>
            <Plus className="mr-2 h-4 w-4" /> Thêm loại phòng
          </Button>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Tổng loại phòng" value={stats.total} />
        <SummaryCard label="Đang hoạt động" value={stats.active} />
        <SummaryCard label="Đã vô hiệu hóa" value={stats.deactivated} />
      </div>

      <div className="relative max-w-xl">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
        <Input
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPage(1)
          }}
          placeholder="Tìm theo mã hoặc tên loại phòng..."
          className="pl-9"
        />
      </div>

      {isLoading ? (
        <PageSkeleton tableOnly />
      ) : loadError ? (
        <Card>
          <CardContent className="flex min-h-48 flex-col items-center justify-center gap-3 text-center">
            <p className="text-sm text-[var(--destructive)]">{loadError}</p>
            <Button variant="outline" onClick={() => void loadData()}>
              <RefreshCw className="mr-2 h-4 w-4" /> Thử lại
            </Button>
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={pagedRoomTypes}
          keyExtractor={(roomType) => roomType.code}
          emptyMessage={search ? "Không tìm thấy loại phòng phù hợp" : "Chưa có loại phòng nào"}
          pagination={{
            page: currentPage,
            pageSize: PAGE_SIZE,
            total: filteredRoomTypes.length,
            onPageChange: setPage,
          }}
          actions={actions.length ? actions : undefined}
        />
      )}

      <RoomTypeFormDialog
        open={formOpen}
        roomType={editingRoomType}
        amenities={amenities}
        canUploadImages={canUpdate}
        onOpenChange={setFormOpen}
        onSaved={loadData}
      />

      <Dialog open={deletingRoomType !== null} onOpenChange={(open) => !open && !isDeleting && setDeletingRoomType(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Xóa loại phòng?</DialogTitle>
            <DialogDescription>
              Loại phòng <strong>{deletingRoomType?.name}</strong> ({deletingRoomType?.code}) sẽ bị vô hiệu hóa
              và ẩn khỏi danh sách. Dữ liệu lịch sử vẫn được giữ lại.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" disabled={isDeleting} onClick={() => setDeletingRoomType(null)}>Hủy</Button>
            <Button variant="destructive" disabled={isDeleting} onClick={() => void confirmDelete()}>
              {isDeleting ? "Đang xóa..." : "Xác nhận xóa"}
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
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-80 w-full" />
    </div>
  )
}
