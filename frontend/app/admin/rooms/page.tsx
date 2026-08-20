"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/status-badge"
import { DataTable } from "@/components/ui/data-table"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Bed, Search, Plus, Filter } from "lucide-react"

// Mock data
const rooms = [
  { id: "101", type: "Standard", floor: "1", status: "available", price: "500,000", view: "City" },
  { id: "102", type: "Standard", floor: "1", status: "dirty", price: "500,000", view: "City" },
  { id: "103", type: "Deluxe", floor: "1", status: "occupied", price: "800,000", view: "Pool" },
  { id: "201", type: "Deluxe", floor: "2", status: "available", price: "800,000", view: "Sea" },
  { id: "202", type: "Suite", floor: "2", status: "maintenance", price: "1,500,000", view: "Sea" },
  { id: "203", type: "Standard", floor: "2", status: "available", price: "500,000", view: "Garden" },
  { id: "301", type: "Suite", floor: "3", status: "occupied", price: "1,500,000", view: "Sea" },
  { id: "302", type: "Deluxe", floor: "3", status: "cleaning", price: "800,000", view: "Pool" },
  { id: "401", type: "VIP Suite", floor: "4", status: "available", price: "2,500,000", view: "Panorama" },
  { id: "402", type: "VIP Suite", floor: "4", status: "occupied", price: "2,500,000", view: "Panorama" },
]

const statusLabels: Record<string, { label: string; variant: "available" | "occupied" | "dirty" | "cleaning" | "maintenance" }> = {
  available: { label: "Trống", variant: "available" },
  occupied: { label: "Đã đặt", variant: "occupied" },
  dirty: { label: "Cần dọn", variant: "dirty" },
  cleaning: { label: "Đang dọn", variant: "cleaning" },
  maintenance: { label: "Bảo trì", variant: "maintenance" },
}

const columns = [
  {
    key: "id",
    header: "Phòng",
    render: (row: typeof rooms[0]) => (
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[var(--muted)]">
          <Bed className="h-4 w-4" />
        </div>
        <span className="font-medium">{row.id}</span>
      </div>
    ),
  },
  { key: "type", header: "Loại phòng" },
  { key: "floor", header: "Tầng" },
  {
    key: "status",
    header: "Trạng thái",
    render: (row: typeof rooms[0]) => (
      <Badge variant={statusLabels[row.status]?.variant || "default"}>
        {statusLabels[row.status]?.label || row.status}
      </Badge>
    ),
  },
  { key: "price", header: "Giá/đêm", className: "text-right" },
  { key: "view", header: "View" },
]

const floorStats = [
  { floor: "Tầng 1", total: 15, available: 8, occupied: 4, dirty: 2, maintenance: 1 },
  { floor: "Tầng 2", total: 12, available: 6, occupied: 6, dirty: 0, maintenance: 0 },
  { floor: "Tầng 3", total: 10, available: 5, occupied: 3, dirty: 1, maintenance: 1 },
  { floor: "Tầng 4", total: 8, available: 3, occupied: 4, dirty: 0, maintenance: 1 },
]

export default function AdminRoomsPage() {
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("all")

  const filteredRooms = rooms.filter((room) => {
    const matchesSearch = room.id.toLowerCase().includes(search.toLowerCase()) ||
      room.type.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = statusFilter === "all" || room.status === statusFilter
    return matchesSearch && matchesStatus
  })

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--foreground)]">Quản lý phòng</h1>
          <p className="text-sm text-[var(--muted-foreground)]">
            Quản lý và theo dõi tình trạng tất cả phòng
          </p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          Thêm phòng
        </Button>
      </div>

      {/* Stats by Floor */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {floorStats.map((floor) => (
          <Card key={floor.floor}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">{floor.floor}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{floor.total} phòng</div>
              <div className="mt-2 flex gap-2 text-xs">
                <span className="text-green-600">{floor.available} trống</span>
                <span className="text-blue-600">{floor.occupied} đã đặt</span>
                <span className="text-orange-500">{floor.dirty + floor.maintenance} khác</span>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <Input
            placeholder="Tìm phòng..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]">
            <Filter className="mr-2 h-4 w-4" />
            <SelectValue placeholder="Trạng thái" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tất cả trạng thái</SelectItem>
            <SelectItem value="available">Trống</SelectItem>
            <SelectItem value="occupied">Đã đặt</SelectItem>
            <SelectItem value="dirty">Cần dọn</SelectItem>
            <SelectItem value="cleaning">Đang dọn</SelectItem>
            <SelectItem value="maintenance">Bảo trì</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Rooms Table */}
      <DataTable
        columns={columns}
        data={filteredRooms}
        keyExtractor={(row) => row.id}
        emptyMessage="Không tìm thấy phòng nào"
      />

      {/* Legend */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap gap-6 text-sm">
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-green-500" />
              <span>Trống - Sẵn sàng đặt</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-blue-500" />
              <span>Đã đặt - Đang có khách</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-orange-400" />
              <span>Cần dọn - Chờ housekeeping</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-purple-500" />
              <span>Đang dọn - Housekeeping đang làm</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-red-500" />
              <span>Bảo trì - Không bán được</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
