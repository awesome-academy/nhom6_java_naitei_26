"use client"

import { useEffect, useMemo, useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { Loader2 } from "lucide-react"
import { z } from "zod"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { createRoom, updateRoom } from "@/lib/api/rooms"
import type { Room, RoomCreateRequest, RoomView } from "@/types/room"
import type { RoomType } from "@/types/room-type"

const roomViews = ["SEA", "CITY", "GARDEN", "POOL", "MOUNTAIN", "NONE"] as const

export const roomViewLabels: Record<RoomView, string> = {
  SEA: "Biển",
  CITY: "Thành phố",
  GARDEN: "Vườn",
  POOL: "Hồ bơi",
  MOUNTAIN: "Núi",
  NONE: "Không có view",
}

const roomSchema = z.object({
  roomNumber: z
    .string()
    .trim()
    .min(1, "Số phòng là bắt buộc")
    .max(20, "Số phòng tối đa 20 ký tự")
    .regex(/^[A-Za-z0-9_-]+$/, "Chỉ dùng chữ, số, dấu gạch dưới hoặc gạch ngang"),
  roomTypeCode: z.string().trim().min(1, "Loại phòng là bắt buộc"),
  viewType: z.enum(roomViews),
  floor: z.number({ error: "Tầng phải là số nguyên" }).int("Tầng phải là số nguyên").nullable(),
  priceOverride: z.string().trim()
    .refine(
      (value) => value === "" || /^\d{1,12}(\.\d{1,2})?$/.test(value),
      "Giá riêng phải là số không âm, tối đa 12 chữ số và 2 chữ số thập phân"
    ),
})

type RoomFormValues = z.infer<typeof roomSchema>

interface RoomFormDialogProps {
  open: boolean
  room: Room | null
  roomTypes: RoomType[]
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}

function defaultValues(room: Room | null): RoomFormValues {
  return {
    roomNumber: room?.roomNumber ?? "",
    roomTypeCode: room?.roomTypeCode ?? "",
    viewType: room?.viewType ?? "NONE",
    floor: room?.floor ?? null,
    priceOverride: room?.priceOverride == null ? "" : String(room.priceOverride),
  }
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return "Không thể lưu phòng. Vui lòng thử lại."
}

export function RoomFormDialog({
  open,
  room,
  roomTypes,
  onOpenChange,
  onSaved,
}: RoomFormDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<RoomFormValues>({
    resolver: zodResolver(roomSchema),
    defaultValues: defaultValues(room),
  })
  const selectedRoomTypeCode = useWatch({ control: form.control, name: "roomTypeCode" })
  const selectedView = useWatch({ control: form.control, name: "viewType" })
  const selectedRoomType = roomTypes.find((roomType) => roomType.code === selectedRoomTypeCode)

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => form.reset(defaultValues(room)), 0)
    return () => window.clearTimeout(timer)
  }, [form, open, room])

  const selectableRoomTypes = useMemo(() => {
    return roomTypes.filter(
      (roomType) => roomType.isActive || roomType.code === room?.roomTypeCode
    )
  }, [room, roomTypes])

  async function submit(values: RoomFormValues) {
    setIsSubmitting(true)
    const normalizedRoomNumber = values.roomNumber.trim().toUpperCase()
    const request = {
      roomTypeCode: values.roomTypeCode.trim().toUpperCase(),
      viewType: values.viewType,
      floor: values.floor,
      priceOverride: values.priceOverride.trim() === "" ? null : Number(values.priceOverride),
    }

    try {
      if (room) {
        await updateRoom(room.roomNumber, request)
      } else {
        const createRequest: RoomCreateRequest = {
          roomNumber: normalizedRoomNumber,
          ...request,
        }
        await createRoom(createRequest)
      }
      await onSaved()
      toast.success(room ? "Đã cập nhật phòng" : "Đã tạo phòng")
      onOpenChange(false)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="max-w-2xl p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>{room ? `Chỉnh sửa phòng ${room.roomNumber}` : "Thêm phòng"}</DialogTitle>
          <DialogDescription>
            Gán loại phòng, tầng, hướng nhìn và giá riêng cho phòng.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(submit)}>
          <div className="grid gap-5 px-6 py-5 sm:grid-cols-2">
            <Field label="Số phòng" error={form.formState.errors.roomNumber?.message}>
              <Input
                disabled={room !== null}
                placeholder="P101"
                {...form.register("roomNumber", {
                  onChange: (event) => {
                    event.target.value = event.target.value.toUpperCase()
                  },
                })}
              />
            </Field>

            <Field label="Loại phòng" error={form.formState.errors.roomTypeCode?.message}>
              <Select
                value={selectedRoomTypeCode}
                onValueChange={(value) => form.setValue("roomTypeCode", value, { shouldValidate: true })}
              >
                <SelectTrigger><SelectValue placeholder="Chọn loại phòng" /></SelectTrigger>
                <SelectContent>
                  {selectableRoomTypes.map((roomType) => (
                    <SelectItem key={roomType.code} value={roomType.code}>
                      {roomType.name} ({roomType.code}){roomType.isActive ? "" : " — đã vô hiệu hóa"}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field label="Tầng" error={form.formState.errors.floor?.message}>
              <Input
                type="number"
                step="1"
                placeholder="Để trống nếu chưa gán"
                {...form.register("floor", {
                  setValueAs: (value) => value === "" ? null : Number(value),
                })}
              />
            </Field>

            <Field label="View" error={form.formState.errors.viewType?.message}>
              <Select
                value={selectedView}
                onValueChange={(value: RoomView) => form.setValue("viewType", value, { shouldValidate: true })}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {roomViews.map((view) => (
                    <SelectItem key={view} value={view}>{roomViewLabels[view]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <div className="sm:col-span-2">
              <Field label="Giá riêng/đêm" error={form.formState.errors.priceOverride?.message}>
                <Input
                  type="number"
                  min={0}
                  step="0.01"
                  placeholder="Để trống để dùng giá của loại phòng"
                  {...form.register("priceOverride")}
                />
              </Field>
              <p className="mt-2 text-xs text-[var(--muted-foreground)]">
                {selectedRoomType
                  ? `Để trống để dùng giá cơ bản ${new Intl.NumberFormat("vi-VN", {
                    style: "currency",
                    currency: selectedRoomType.currency,
                    maximumFractionDigits: 0,
                  }).format(selectedRoomType.basePrice)} của loại phòng.`
                  : "Để trống sẽ dùng giá cơ bản đang cấu hình ở loại phòng."}
              </p>
            </div>
          </div>

          <DialogFooter className="border-t px-6 py-4">
            <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting || selectableRoomTypes.length === 0}>
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {room ? "Lưu thay đổi" : "Tạo phòng"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-xs text-[var(--destructive)]">{error}</p>}
    </div>
  )
}
