"use client"

import { useEffect, useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { addDays, format, isValid, parseISO } from "date-fns"
import { useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { Loader2 } from "lucide-react"
import { z } from "zod"

import { blockTypeLabels } from "@/components/admin/maintenance/block-config"
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
import { Textarea } from "@/components/ui/textarea"
import {
  createRoomStatusBlock,
  getRoomStatusBlocks,
} from "@/lib/api/room-status-blocks"
import type { Room } from "@/types/room"
import type { RoomBlockType, RoomStatusBlock } from "@/types/room-status-block"

const roomBlockTypes = [
  "MAINTENANCE",
  "RENOVATION",
  "OUT_OF_SERVICE",
  "INTERNAL_USE",
  "DEEP_CLEANING",
] as const

const dateSchema = z
  .string()
  .min(1, "Ngày là bắt buộc")
  .refine((value) => {
    const parsed = parseISO(value)
    return isValid(parsed) && format(parsed, "yyyy-MM-dd") === value
  }, "Ngày không hợp lệ")

const blockSchema = z
  .object({
    roomNumber: z.string().trim().min(1, "Phòng là bắt buộc"),
    blockType: z.enum(roomBlockTypes),
    startDate: dateSchema,
    endDate: dateSchema,
    reason: z.string().trim().max(10_000, "Ghi chú tối đa 10.000 ký tự"),
  })
  .superRefine((values, context) => {
    if (values.startDate && values.endDate && values.endDate <= values.startDate) {
      context.addIssue({
        code: "custom",
        path: ["endDate"],
        message: "Ngày kết thúc phải sau ngày bắt đầu",
      })
    }
  })

type BlockFormValues = z.infer<typeof blockSchema>

interface BlockFormDialogProps {
  open: boolean
  rooms: Room[]
  initialRoomNumber: string
  initialStartDate: string
  initialEndDate: string
  onOpenChange: (open: boolean) => void
  onCreated: (block: RoomStatusBlock) => Promise<void>
}

function getDefaultValues(
  roomNumber: string,
  startDate: string,
  endDate: string
): BlockFormValues {
  return {
    roomNumber,
    blockType: "MAINTENANCE",
    startDate,
    endDate,
    reason: "",
  }
}

function getErrorMessage(error: unknown): string {
  const status = (error as { status?: number })?.status
  if (status === 409) {
    return "Phòng đã có lịch bảo trì hoặc booking hiệu lực trong khoảng đã chọn."
  }
  if (error instanceof Error && error.message) return error.message
  return "Không thể tạo lịch bảo trì. Vui lòng thử lại."
}

export function BlockFormDialog({
  open,
  rooms,
  initialRoomNumber,
  initialStartDate,
  initialEndDate,
  onOpenChange,
  onCreated,
}: BlockFormDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<BlockFormValues>({
    resolver: zodResolver(blockSchema),
    defaultValues: getDefaultValues(initialRoomNumber, initialStartDate, initialEndDate),
  })
  const selectedRoomNumber = useWatch({ control: form.control, name: "roomNumber" })
  const selectedBlockType = useWatch({ control: form.control, name: "blockType" })

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      form.reset(getDefaultValues(initialRoomNumber, initialStartDate, initialEndDate))
    }, 0)
    return () => window.clearTimeout(timer)
  }, [form, initialEndDate, initialRoomNumber, initialStartDate, open])

  async function submit(values: BlockFormValues) {
    setIsSubmitting(true)
    form.clearErrors("root")
    try {
      const overlappingBlocks = await getRoomStatusBlocks(values.startDate, values.endDate)
      const hasRoomOverlap = overlappingBlocks.some(
        (block) => block.roomNumber === values.roomNumber &&
          block.startDate < values.endDate &&
          block.endDate > values.startDate
      )
      if (hasRoomOverlap) {
        form.setError("root", {
          message: "Khoảng ngày này trùng lịch bảo trì hiện có của cùng phòng.",
        })
        return
      }

      const created = await createRoomStatusBlock({
        roomNumber: values.roomNumber,
        blockType: values.blockType,
        startDate: values.startDate,
        endDate: values.endDate,
        reason: values.reason.trim() || null,
      })
      await onCreated(created)
      toast.success("Đã tạo lịch bảo trì")
      onOpenChange(false)
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="max-w-2xl p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>Tạo lịch bảo trì</DialogTitle>
          <DialogDescription>
            Khoảng ngày sử dụng quy ước nửa mở: ngày kết thúc không nằm trong lịch block.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(submit)}>
          <div className="grid gap-5 px-6 py-5 sm:grid-cols-2">
            <Field label="Phòng" error={form.formState.errors.roomNumber?.message}>
              <Select
                value={selectedRoomNumber}
                onValueChange={(value) => form.setValue("roomNumber", value, { shouldValidate: true })}
              >
                <SelectTrigger><SelectValue placeholder="Chọn phòng" /></SelectTrigger>
                <SelectContent>
                  {rooms.map((room) => (
                    <SelectItem key={room.roomNumber} value={room.roomNumber}>
                      {room.roomNumber} · {room.roomTypeName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field label="Loại block" error={form.formState.errors.blockType?.message}>
              <Select
                value={selectedBlockType}
                onValueChange={(value: RoomBlockType) => form.setValue("blockType", value, { shouldValidate: true })}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {roomBlockTypes.map((blockType) => (
                    <SelectItem key={blockType} value={blockType}>{blockTypeLabels[blockType]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field label="Ngày bắt đầu" error={form.formState.errors.startDate?.message}>
              <Input
                type="date"
                {...form.register("startDate", {
                  onChange: (event) => {
                    const currentEndDate = form.getValues("endDate")
                    if (event.target.value && currentEndDate <= event.target.value) {
                      form.setValue(
                        "endDate",
                        format(addDays(parseISO(event.target.value), 1), "yyyy-MM-dd"),
                        { shouldValidate: true }
                      )
                    }
                  },
                })}
              />
            </Field>

            <Field label="Ngày kết thúc (không bao gồm)" error={form.formState.errors.endDate?.message}>
              <Input type="date" {...form.register("endDate")} />
            </Field>

            <div className="sm:col-span-2">
              <Field label="Ghi chú" error={form.formState.errors.reason?.message}>
                <Textarea
                  rows={4}
                  maxLength={10_000}
                  placeholder="Mô tả lý do hoặc công việc cần thực hiện..."
                  {...form.register("reason")}
                />
              </Field>
            </div>

            {form.formState.errors.root?.message && (
              <p className="sm:col-span-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {form.formState.errors.root.message}
              </p>
            )}
          </div>

          <DialogFooter className="border-t px-6 py-4">
            <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting || rooms.length === 0}>
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Tạo lịch
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
