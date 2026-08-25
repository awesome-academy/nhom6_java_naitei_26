"use client"

import { useEffect, useMemo, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2 } from "lucide-react"
import { useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
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
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { createFolioCharge } from "@/lib/api/folio"
import type { FolioChargeResponse } from "@/types/booking-staff"
import type { ServiceCategory, ServiceItemOption } from "@/types/folio"

const quantityPattern = /^\d{1,8}(?:\.\d{1,2})?$/
const formSchema = z.object({
  serviceItemCode: z.string().trim().min(1, "Vui lòng chọn dịch vụ"),
  quantity: z.string().trim()
    .min(1, "Số lượng là bắt buộc")
    .regex(quantityPattern, "Số lượng tối đa 8 chữ số và 2 số thập phân")
    .refine((value) => Number(value) > 0, "Số lượng phải lớn hơn 0"),
})

type FormValues = z.infer<typeof formSchema>

const categoryLabels: Record<ServiceCategory, string> = {
  FNB: "Ẩm thực",
  LAUNDRY: "Giặt ủi",
  SPA: "Spa",
  TRANSPORT: "Di chuyển",
  MINIBAR: "Minibar",
  PENALTY: "Phụ phí",
  OTHER: "Khác",
}

interface AddFolioChargeDialogProps {
  open: boolean
  bookingPublicId: string
  currency: string
  serviceItems: ServiceItemOption[]
  onOpenChange: (open: boolean) => void
  onCreated: (charge: FolioChargeResponse) => void
}

function formatMoney(value: number, currency: string): string {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(value)
}

function getErrorMessage(error: unknown): string {
  const status = (error as { status?: number })?.status
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền thêm khoản phát sinh."
  if (status === 404) return "Dịch vụ không còn hoạt động. Vui lòng tải lại danh mục."
  if (status === 400) return "Khoản phát sinh không hợp lệ hoặc booking không ở trạng thái đang lưu trú."
  if (error instanceof Error && error.message) return error.message
  return "Không thể thêm khoản phát sinh. Vui lòng thử lại."
}

export function AddFolioChargeDialog({
  open,
  bookingPublicId,
  currency,
  serviceItems,
  onOpenChange,
  onCreated,
}: AddFolioChargeDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { serviceItemCode: "", quantity: "1" },
  })
  const selectedCode = useWatch({ control: form.control, name: "serviceItemCode" })
  const quantity = useWatch({ control: form.control, name: "quantity" })

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      form.reset({ serviceItemCode: "", quantity: "1" })
    }, 0)
    return () => window.clearTimeout(timer)
  }, [form, open])

  const groupedItems = useMemo(() => serviceItems.reduce((groups, item) => {
    const current = groups.get(item.category) ?? []
    current.push(item)
    groups.set(item.category, current)
    return groups
  }, new Map<ServiceCategory, ServiceItemOption[]>()), [serviceItems])

  const selectedItem = serviceItems.find((item) => item.code === selectedCode)
  const quantityValue = quantityPattern.test(quantity?.trim() ?? "") ? Number(quantity) : 0
  const subtotal = selectedItem ? selectedItem.unitPrice * quantityValue : 0
  const taxAmount = selectedItem ? subtotal * selectedItem.taxPercent / 100 : 0

  async function submit(values: FormValues) {
    setIsSubmitting(true)
    form.clearErrors("root")
    try {
      const created = await createFolioCharge(bookingPublicId, {
        serviceItemCode: values.serviceItemCode,
        quantity: Number(values.quantity),
      })
      onCreated(created)
      toast.success("Đã thêm khoản phát sinh")
      onOpenChange(false)
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="max-w-xl p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>Thêm khoản phát sinh</DialogTitle>
          <DialogDescription>
            Giá và thuế được lấy từ danh mục dịch vụ tại thời điểm ghi nhận.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)}>
            <div className="flex flex-col gap-5 px-6 py-5">
              <FormField
                control={form.control}
                name="serviceItemCode"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Dịch vụ</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger><SelectValue placeholder="Chọn loại dịch vụ" /></SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {[...groupedItems.entries()].map(([category, items]) => (
                          <SelectGroup key={category}>
                            <SelectLabel>{categoryLabels[category]}</SelectLabel>
                            {items.map((item) => (
                              <SelectItem key={item.code} value={item.code}>
                                {item.name} · {formatMoney(item.unitPrice, currency)}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="quantity"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Số lượng</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min="0.01"
                        max="99999999.99"
                        step="0.01"
                        inputMode="decimal"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>Có thể nhập tối đa 2 chữ số thập phân.</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className="rounded-lg border bg-[var(--muted)]/40 p-4">
                <div className="flex items-center justify-between gap-4 text-sm">
                  <span className="text-[var(--muted-foreground)]">Đơn giá</span>
                  <span>{formatMoney(selectedItem?.unitPrice ?? 0, currency)}</span>
                </div>
                <div className="mt-2 flex items-center justify-between gap-4 text-sm">
                  <span className="text-[var(--muted-foreground)]">
                    Thuế ({selectedItem?.taxPercent ?? 0}%)
                  </span>
                  <span>{formatMoney(taxAmount, currency)}</span>
                </div>
                <div className="mt-3 flex items-center justify-between gap-4 border-t pt-3 font-semibold">
                  <span>Thành tiền dự kiến</span>
                  <span>{formatMoney(subtotal + taxAmount, currency)}</span>
                </div>
              </div>
              {form.formState.errors.root?.message && (
                <p className="text-sm text-[var(--destructive)]" role="alert">
                  {form.formState.errors.root.message}
                </p>
              )}
            </div>
            <DialogFooter className="border-t px-6 py-4">
              <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => onOpenChange(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={isSubmitting || serviceItems.length === 0}>
                {isSubmitting && <Loader2 data-icon="inline-start" className="animate-spin" />}
                Thêm khoản phát sinh
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
