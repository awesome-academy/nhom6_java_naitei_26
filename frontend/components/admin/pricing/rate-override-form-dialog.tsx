"use client"

import { useEffect, useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { addDays, format, isValid, parseISO } from "date-fns"
import { Loader2 } from "lucide-react"
import { useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import {
  hasApplicableDateConflict,
  weekdayOptions,
} from "@/components/admin/pricing/pricing-utils"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
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
import { createRoomTypeRateOverride, updateRateOverride } from "@/lib/api/rate-overrides"
import type { RateOverride } from "@/types/rate-override"
import type { RoomType } from "@/types/room-type"

const dateSchema = z
  .string()
  .min(1, "Ngày là bắt buộc")
  .refine((value) => {
    const parsed = parseISO(value)
    return isValid(parsed) && format(parsed, "yyyy-MM-dd") === value
  }, "Ngày không hợp lệ")

const formSchema = z
  .object({
    name: z.string().trim().min(1, "Tên rule là bắt buộc").max(120, "Tên tối đa 120 ký tự"),
    roomTypeCode: z.string().trim().min(1, "Loại phòng là bắt buộc"),
    startDate: dateSchema,
    endDate: dateSchema,
    price: z.string().trim().regex(
      /^\d{1,12}(\.\d{1,2})?$/,
      "Giá phải không âm, tối đa 12 chữ số và 2 số lẻ"
    ),
    priority: z.string().trim().regex(/^-?\d+$/, "Priority phải là số nguyên"),
    weekdays: z.array(z.number().int().min(1).max(7)).min(1, "Chọn ít nhất một ngày"),
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

type FormValues = z.infer<typeof formSchema>

interface RateOverrideFormDialogProps {
  open: boolean
  override: RateOverride | null
  roomTypes: RoomType[]
  activeOverrides: RateOverride[]
  onOpenChange: (open: boolean) => void
  onSaved: (override: RateOverride) => Promise<void>
}

function defaultValues(roomTypes: RoomType[]): FormValues {
  const today = format(new Date(), "yyyy-MM-dd")
  return {
    name: "",
    roomTypeCode: roomTypes[0]?.code ?? "",
    startDate: today,
    endDate: format(addDays(new Date(), 1), "yyyy-MM-dd"),
    price: "",
    priority: "0",
    weekdays: weekdayOptions.map((day) => day.value),
  }
}

function valuesFromOverride(override: RateOverride): FormValues {
  return {
    name: override.name,
    roomTypeCode: override.roomTypeCode,
    startDate: override.startDate,
    endDate: override.endDate,
    price: String(override.price),
    priority: String(override.priority),
    weekdays: override.weekdays ?? weekdayOptions.map((day) => day.value),
  }
}

function getErrorMessage(error: unknown): string {
  if ((error as { status?: number })?.status === 409) {
    return "Đã có rule cùng loại phòng, priority và ngày áp dụng trong khoảng này."
  }
  if (error instanceof Error && error.message) return error.message
  return "Không thể lưu rule giá. Vui lòng thử lại."
}

export function RateOverrideFormDialog({
  open,
  override,
  roomTypes,
  activeOverrides,
  onOpenChange,
  onSaved,
}: RateOverrideFormDialogProps) {
  const isEditMode = override !== null
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: defaultValues(roomTypes),
  })
  const roomTypeCode = useWatch({ control: form.control, name: "roomTypeCode" })
  const selectedWeekdays = useWatch({ control: form.control, name: "weekdays" }) ?? []

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      form.reset(override ? valuesFromOverride(override) : defaultValues(roomTypes))
    }, 0)
    return () => window.clearTimeout(timer)
  }, [form, open, override, roomTypes])

  function toggleWeekday(weekday: number, checked: boolean) {
    const next = checked
      ? [...new Set([...selectedWeekdays, weekday])].sort()
      : selectedWeekdays.filter((value) => value !== weekday)
    form.setValue("weekdays", next, { shouldDirty: true, shouldValidate: true })
  }

  async function submit(values: FormValues) {
    setIsSubmitting(true)
    form.clearErrors("root")
    try {
      const weekdays = values.weekdays.length === 7 ? null : values.weekdays
      const candidate = {
        roomTypeCode: values.roomTypeCode,
        startDate: values.startDate,
        endDate: values.endDate,
        weekdays,
        priority: Number(values.priority),
      }
      if (activeOverrides.some((rule) => (
        rule.id !== override?.id && hasApplicableDateConflict(candidate, rule)
      ))) {
        form.setError("root", {
          message: "Rule bị trùng ngày áp dụng với một rule cùng priority của loại phòng này.",
        })
        return
      }

      const roomType = roomTypes.find((item) => item.code === values.roomTypeCode)
      if (!roomType) {
        form.setError("root", { message: "Không tìm thấy loại phòng đã chọn." })
        return
      }

      const request = {
        name: values.name.trim(),
        startDate: values.startDate,
        endDate: values.endDate,
        price: Number(values.price),
        weekdays,
        priority: Number(values.priority),
      }
      const saved = override
        ? await updateRateOverride(override.id, { ...request, roomTypeId: roomType.roomTypeId })
        : await createRoomTypeRateOverride(values.roomTypeCode, request)
      await onSaved(saved)
      toast.success(isEditMode ? "Đã cập nhật rule giá" : "Đã tạo rule giá")
      onOpenChange(false)
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !isSubmitting && onOpenChange(next)}>
      <DialogContent className="max-w-2xl p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>{isEditMode ? "Chỉnh sửa rate override" : "Tạo rate override"}</DialogTitle>
          <DialogDescription>
            Rule dùng khoảng ngày nửa mở: ngày kết thúc không được áp dụng giá.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(submit)}>
          <div className="grid gap-5 px-6 py-5 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <Field label="Tên rule" error={form.formState.errors.name?.message}>
                <Input maxLength={120} placeholder="Ví dụ: Cuối tuần mùa hè" {...form.register("name")} />
              </Field>
            </div>

            <Field label="Loại phòng" error={form.formState.errors.roomTypeCode?.message}>
              <Select
                value={roomTypeCode}
                onValueChange={(value) => form.setValue("roomTypeCode", value, { shouldValidate: true })}
              >
                <SelectTrigger><SelectValue placeholder="Chọn loại phòng" /></SelectTrigger>
                <SelectContent>
                  {roomTypes.map((roomType) => (
                    <SelectItem key={roomType.code} value={roomType.code}>
                      {roomType.code} · {roomType.name}{roomType.isActive ? "" : " (Ngừng hoạt động)"}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field label="Priority" error={form.formState.errors.priority?.message}>
              <Input inputMode="numeric" {...form.register("priority")} />
            </Field>

            <Field label="Ngày bắt đầu" error={form.formState.errors.startDate?.message}>
              <Input type="date" {...form.register("startDate")} />
            </Field>

            <Field label="Ngày kết thúc (không bao gồm)" error={form.formState.errors.endDate?.message}>
              <Input type="date" {...form.register("endDate")} />
            </Field>

            <div className="sm:col-span-2">
              <Field label="Giá áp dụng" error={form.formState.errors.price?.message}>
                <Input inputMode="decimal" placeholder="1500000" {...form.register("price")} />
              </Field>
            </div>

            <div className="sm:col-span-2">
              <Field label="Ngày trong tuần" error={form.formState.errors.weekdays?.message}>
                <div className="grid grid-cols-4 gap-2 sm:grid-cols-7">
                  {weekdayOptions.map((day) => (
                    <label
                      key={day.value}
                      className="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm hover:bg-[var(--muted)]"
                      title={day.label}
                    >
                      <Checkbox
                        checked={selectedWeekdays.includes(day.value)}
                        onCheckedChange={(checked) => toggleWeekday(day.value, checked === true)}
                      />
                      {day.shortLabel}
                    </label>
                  ))}
                </div>
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
            <Button type="submit" disabled={isSubmitting || roomTypes.length === 0}>
              {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEditMode ? "Lưu thay đổi" : "Tạo rule"}
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
