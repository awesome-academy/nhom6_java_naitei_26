import { BedDouble, ImageIcon, Pencil } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { roomViewLabels } from "@/components/admin/rooms/room-form-dialog"
import type { HousekeepingStatus, Room, RoomOperationalStatus } from "@/types/room"
import type { RoomType } from "@/types/room-type"

const housekeepingLabels: Record<HousekeepingStatus, string> = {
  CLEAN: "Sạch",
  DIRTY: "Bẩn",
  CLEANING: "Đang dọn",
}

const operationalLabels: Record<RoomOperationalStatus, string> = {
  ACTIVE: "Hoạt động",
  MAINTENANCE: "Bảo trì",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  RENOVATION: "Cải tạo",
}

function formatPrice(value: number, currency = "VND"): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value)
}

interface RoomDetailSheetProps {
  room: Room | null
  roomTypes: RoomType[]
  canUpdate: boolean
  onOpenChange: (open: boolean) => void
  onEdit: (room: Room) => void
}

export function RoomDetailSheet({
  room,
  roomTypes,
  canUpdate,
  onOpenChange,
  onEdit,
}: RoomDetailSheetProps) {
  const roomType = roomTypes.find((item) => item.code === room?.roomTypeCode)
  const roomImage = room?.images.find((image) => image.isPrimary) ?? room?.images[0]
  const roomTypeImage = roomType?.images.find((image) => image.isPrimary) ?? roomType?.images[0]
  const primaryImage = roomImage ?? roomTypeImage
  const displayedPrice = room?.priceOverride ?? roomType?.basePrice

  return (
    <Sheet open={room !== null} onOpenChange={onOpenChange}>
      <SheetContent>
        {room && (
          <>
            <SheetHeader className="border-b px-6 py-5 pr-12">
              <SheetTitle className="flex items-center gap-2">
                <BedDouble className="h-5 w-5" /> Phòng {room.roomNumber}
              </SheetTitle>
              <SheetDescription>{room.roomTypeName} ({room.roomTypeCode})</SheetDescription>
            </SheetHeader>

            <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
              <div className="flex aspect-video items-center justify-center overflow-hidden rounded-xl bg-[var(--muted)]">
                {primaryImage ? (
                  <div
                    role="img"
                    aria-label={primaryImage.altText}
                    className="h-full w-full bg-cover bg-center"
                    style={{ backgroundImage: `url("${primaryImage.downloadUrl}")` }}
                  />
                ) : (
                  <ImageIcon className="h-8 w-8 text-[var(--muted-foreground)]" />
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <StatusBlock label="Vận hành">
                  <Badge variant={room.operationalStatus === "ACTIVE" ? "success" : "destructive"}>
                    {operationalLabels[room.operationalStatus]}
                  </Badge>
                </StatusBlock>
                <StatusBlock label="Housekeeping">
                  <Badge variant={getHousekeepingVariant(room.housekeepingStatus)}>
                    {housekeepingLabels[room.housekeepingStatus]}
                  </Badge>
                </StatusBlock>
              </div>

              <Separator />

              <dl className="space-y-4 text-sm">
                <DetailRow label="Tầng" value={room.floor === null ? "Chưa gán" : String(room.floor)} />
                <DetailRow label="View" value={roomViewLabels[room.viewType]} />
                <DetailRow
                  label="Giá/đêm"
                  value={displayedPrice === undefined ? "—" : formatPrice(displayedPrice, roomType?.currency)}
                />
                <DetailRow
                  label="Nguồn giá"
                  value={room.priceOverride === null ? "Giá cơ bản loại phòng" : "Giá riêng của phòng"}
                />
              </dl>

              <Separator />

              <section className="space-y-3">
                <h3 className="text-sm font-semibold">Tiện nghi</h3>
                <div className="flex flex-wrap gap-2">
                  {room.amenities.map((amenity) => (
                    <Badge key={amenity.code} variant="secondary">{amenity.name}</Badge>
                  ))}
                  {room.amenities.length === 0 && (
                    <p className="text-sm text-[var(--muted-foreground)]">Chưa có tiện nghi.</p>
                  )}
                </div>
              </section>
            </div>

            {canUpdate && (
              <SheetFooter className="border-t px-6 py-4">
                <Button onClick={() => onEdit(room)}>
                  <Pencil className="mr-2 h-4 w-4" /> Chỉnh sửa phòng
                </Button>
              </SheetFooter>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}

function getHousekeepingVariant(status: HousekeepingStatus) {
  if (status === "CLEAN") return "success" as const
  if (status === "DIRTY") return "destructive" as const
  return "warning" as const
}

function StatusBlock({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2 rounded-lg border p-3">
      <p className="text-xs text-[var(--muted-foreground)]">{label}</p>
      {children}
    </div>
  )
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-[var(--muted-foreground)]">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  )
}
