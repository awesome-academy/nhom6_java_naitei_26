"use client"

import { useMemo, useState } from "react"
import { format, parseISO } from "date-fns"
import { vi } from "date-fns/locale"
import {
  AlertCircle,
  Ban,
  BedDouble,
  Plus,
  ReceiptText,
  RefreshCw,
} from "lucide-react"

import { AddFolioChargeDialog } from "@/components/admin/bookings/add-folio-charge-dialog"
import { VoidFolioChargeDialog } from "@/components/admin/bookings/void-folio-charge-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { DataTable } from "@/components/ui/dataTable"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils"
import type {
  BookingStaffDetail,
  FolioChargeResponse,
} from "@/types/booking-staff"
import type { ServiceItemOption } from "@/types/folio"

interface FolioPanelProps {
  booking: BookingStaffDetail
  serviceItems: ServiceItemOption[]
  canManage: boolean
  isLoadingServiceItems: boolean
  serviceItemsError: string | null
  onRetryServiceItems: () => void
  onChargeChanged: (charge: FolioChargeResponse) => void
}

interface RoomNightRow {
  key: string
  roomNumber: string | null
  roomTypeName: string
  roomTypeCode: string
  stayDate: string
  price: number
}

function formatMoney(value: number, currency: string): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatDate(value: string): string {
  return format(parseISO(value), "dd/MM/yyyy", { locale: vi })
}

function formatDateTime(value: string): string {
  return format(parseISO(value), "HH:mm · dd/MM/yyyy", { locale: vi })
}

export function FolioPanel({
  booking,
  serviceItems,
  canManage,
  isLoadingServiceItems,
  serviceItemsError,
  onRetryServiceItems,
  onChargeChanged,
}: FolioPanelProps) {
  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const [chargeToVoid, setChargeToVoid] = useState<FolioChargeResponse | null>(null)
  const canChangeCharges = canManage && booking.status === "CHECKED_IN"

  const roomNights = useMemo<RoomNightRow[]>(() => {
    return booking.rooms.flatMap((room) =>
      room.nightlyRates.map((night) => ({
        key: `${room.id}-${night.stayDate}`,
        roomNumber: room.roomNumber,
        roomTypeName: room.roomTypeName,
        roomTypeCode: room.roomTypeCode,
        stayDate: night.stayDate,
        price: night.price,
      }))
    )
  }, [booking.rooms])

  const activeCharges = booking.folioCharges.filter((charge) => !charge.isVoided)
  const serviceTaxTotal = activeCharges.reduce(
    (total, charge) => total + charge.taxAmount,
    0
  )
  const serviceLineTotal = activeCharges.reduce(
    (total, charge) => total + charge.lineTotal,
    0
  )

  return (
    <>
      <Card>
        <CardHeader className="flex-row items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <CardTitle>Folio</CardTitle>
            <CardDescription>
              Chi tiết tiền phòng và các khoản dịch vụ phát sinh của booking.
            </CardDescription>
          </div>
          {canChangeCharges && (
            <Button
              onClick={() => setAddDialogOpen(true)}
              disabled={isLoadingServiceItems || Boolean(serviceItemsError) || serviceItems.length === 0}
            >
              <Plus data-icon="inline-start" />
              Thêm khoản phát sinh
            </Button>
          )}
        </CardHeader>

        <CardContent>
          {canManage && isLoadingServiceItems && booking.status === "CHECKED_IN" && (
            <Skeleton className="mb-4 h-10 w-full" />
          )}

          {canManage && serviceItemsError && booking.status === "CHECKED_IN" && (
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm">
              <div className="flex items-center gap-2 text-destructive">
                <AlertCircle className="size-4" aria-hidden="true" />
                <span>{serviceItemsError}</span>
              </div>
              <Button type="button" size="sm" variant="outline" onClick={onRetryServiceItems}>
                <RefreshCw data-icon="inline-start" />
                Thử lại
              </Button>
            </div>
          )}

          {!canChangeCharges && (
            <p className="mb-4 rounded-lg border bg-muted/40 p-3 text-sm text-muted-foreground">
              {booking.status !== "CHECKED_IN"
                ? "Chỉ có thể thêm hoặc hủy khoản phát sinh khi booking đang ở trạng thái Đã nhận phòng."
                : "Bạn chỉ có quyền xem Folio; quyền invoice:issue là bắt buộc để quản lý khoản phát sinh."}
            </p>
          )}

          <Tabs defaultValue="room">
            <TabsList>
              <TabsTrigger value="room">
                <BedDouble data-icon="inline-start" />
                Tiền phòng
              </TabsTrigger>
              <TabsTrigger value="services">
                <ReceiptText data-icon="inline-start" />
                Dịch vụ ({booking.folioCharges.length})
              </TabsTrigger>
            </TabsList>

            <TabsContent value="room" className="mt-4">
              <DataTable
                data={roomNights}
                keyExtractor={(row) => row.key}
                emptyMessage="Booking chưa có dữ liệu tiền phòng."
                columns={[
                  {
                    key: "room",
                    header: "Phòng",
                    render: (row) => (
                      <div>
                        <p className="font-medium">
                          {row.roomNumber ? `Phòng ${row.roomNumber}` : "Chưa gán phòng"}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {row.roomTypeName} · {row.roomTypeCode}
                        </p>
                      </div>
                    ),
                  },
                  {
                    key: "stayDate",
                    header: "Đêm lưu trú",
                    render: (row) => formatDate(row.stayDate),
                  },
                  {
                    key: "price",
                    header: "Giá đêm",
                    className: "text-right",
                    render: (row) => (
                      <span className="font-medium">
                        {formatMoney(row.price, booking.currency)}
                      </span>
                    ),
                  },
                ]}
              />
              <div className="mt-3 flex items-center justify-end gap-8 rounded-lg bg-muted/40 px-4 py-3">
                <span className="text-sm text-muted-foreground">Tổng tiền phòng</span>
                <span className="font-semibold">
                  {formatMoney(booking.roomsTotal, booking.currency)}
                </span>
              </div>
            </TabsContent>

            <TabsContent value="services" className="mt-4">
              <DataTable
                data={booking.folioCharges}
                keyExtractor={(charge) => String(charge.id)}
                emptyMessage="Chưa có khoản dịch vụ phát sinh."
                columns={[
                  {
                    key: "description",
                    header: "Khoản phát sinh",
                    render: (charge) => (
                      <div
                        className={cn(
                          charge.isVoided && "text-muted-foreground line-through"
                        )}
                      >
                        <p className="font-medium">{charge.description}</p>
                        <p className="text-xs text-muted-foreground">
                          {charge.serviceItemCode ?? "Nhập tay"} · {formatDateTime(charge.chargedAt)}
                        </p>
                      </div>
                    ),
                  },
                  {
                    key: "quantity",
                    header: "SL",
                    render: (charge) => (
                      <span className={cn(charge.isVoided && "line-through")}>
                        {charge.quantity}
                      </span>
                    ),
                  },
                  {
                    key: "unitPrice",
                    header: "Đơn giá",
                    className: "text-right",
                    render: (charge) => (
                      <span className={cn(charge.isVoided && "line-through")}>
                        {formatMoney(charge.unitPrice, booking.currency)}
                      </span>
                    ),
                  },
                  {
                    key: "tax",
                    header: "Thuế",
                    className: "text-right",
                    render: (charge) => (
                      <span className={cn(charge.isVoided && "line-through")}>
                        {formatMoney(charge.taxAmount, booking.currency)} ({charge.taxPercent}%)
                      </span>
                    ),
                  },
                  {
                    key: "lineTotal",
                    header: "Thành tiền",
                    className: "text-right",
                    render: (charge) => (
                      <span className={cn("font-medium", charge.isVoided && "line-through")}>
                        {formatMoney(charge.lineTotal, booking.currency)}
                      </span>
                    ),
                  },
                  {
                    key: "status",
                    header: "Trạng thái",
                    render: (charge) =>
                      charge.isVoided ? (
                        <div className="flex flex-col items-start gap-1">
                          <Badge variant="destructive">Đã hủy</Badge>
                          {charge.voidReason && (
                            <span className="max-w-48 text-xs text-muted-foreground">
                              {charge.voidReason}
                            </span>
                          )}
                        </div>
                      ) : (
                        <Badge variant="success">Hiệu lực</Badge>
                      ),
                  },
                  {
                    key: "action",
                    header: "Thao tác",
                    className: "text-right",
                    render: (charge) =>
                      canChangeCharges && !charge.isVoided ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setChargeToVoid(charge)}
                        >
                          <Ban data-icon="inline-start" />
                          Void
                        </Button>
                      ) : null,
                  },
                ]}
              />
              <div className="mt-3 flex flex-col items-end gap-2 rounded-lg bg-muted/40 px-4 py-3 text-sm">
                <div className="flex w-full max-w-sm items-center justify-between gap-6">
                  <span className="text-muted-foreground">Dịch vụ trước thuế</span>
                  <span>{formatMoney(booking.servicesTotal, booking.currency)}</span>
                </div>
                <div className="flex w-full max-w-sm items-center justify-between gap-6">
                  <span className="text-muted-foreground">Thuế dịch vụ</span>
                  <span>{formatMoney(serviceTaxTotal, booking.currency)}</span>
                </div>
                <div className="flex w-full max-w-sm items-center justify-between gap-6 border-t pt-2 font-semibold">
                  <span>Tổng dịch vụ</span>
                  <span>{formatMoney(serviceLineTotal, booking.currency)}</span>
                </div>
              </div>
            </TabsContent>
          </Tabs>
        </CardContent>

        <CardFooter className="flex-col items-stretch gap-3 border-t bg-muted/20 pt-6">
          <div className="ml-auto grid w-full max-w-md grid-cols-2 gap-x-8 gap-y-2 text-sm">
            <span className="text-muted-foreground">Tiền phòng</span>
            <span className="text-right">
              {formatMoney(booking.roomsTotal, booking.currency)}
            </span>
            <span className="text-muted-foreground">Dịch vụ trước thuế</span>
            <span className="text-right">
              {formatMoney(booking.servicesTotal, booking.currency)}
            </span>
            <span className="text-muted-foreground">Thuế</span>
            <span className="text-right">
              {formatMoney(booking.taxTotal, booking.currency)}
            </span>
            <span className="text-muted-foreground">Giảm giá</span>
            <span className="text-right">
              −{formatMoney(booking.discountTotal, booking.currency)}
            </span>
            <span className="border-t pt-3 text-base font-semibold">Tổng cộng</span>
            <span className="border-t pt-3 text-right text-xl font-bold">
              {formatMoney(booking.totalAmount, booking.currency)}
            </span>
          </div>
        </CardFooter>
      </Card>

      <AddFolioChargeDialog
        open={addDialogOpen}
        bookingPublicId={booking.publicId}
        currency={booking.currency}
        serviceItems={serviceItems}
        onOpenChange={setAddDialogOpen}
        onCreated={onChargeChanged}
      />
      <VoidFolioChargeDialog
        open={chargeToVoid !== null}
        bookingPublicId={booking.publicId}
        charge={chargeToVoid}
        onOpenChange={(open) => !open && setChargeToVoid(null)}
        onVoided={onChargeChanged}
      />
    </>
  )
}
