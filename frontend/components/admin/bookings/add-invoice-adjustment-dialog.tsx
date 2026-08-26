"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2 } from "lucide-react"
import { useForm } from "react-hook-form"
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
import { Textarea } from "@/components/ui/textarea"
import { addInvoiceAdjustment } from "@/lib/api/invoices"
import type { InvoiceResponse } from "@/types/booking-staff"

const amountPattern = /^-?\d{1,12}(?:\.\d{1,2})?$/
const formSchema = z.object({
  description: z.string().trim().min(1, "Vui lòng nhập nội dung điều chỉnh").max(200),
  amount: z.string()
    .trim()
    .regex(amountPattern, "Số tiền tối đa 12 chữ số và 2 số thập phân")
    .refine((value) => Number(value) !== 0, "Số tiền phải khác 0"),
})

type FormValues = z.infer<typeof formSchema>

interface AddInvoiceAdjustmentDialogProps {
  open: boolean
  invoicePublicId: string
  currency: string
  onOpenChange: (open: boolean) => void
  onChanged: (invoice: InvoiceResponse) => void
}

function getErrorMessage(error: unknown): string {
  const status = (error as { status?: number })?.status
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền thêm dòng điều chỉnh."
  if (status === 404) return "Không tìm thấy hóa đơn."
  if (status === 409) return "Hóa đơn không còn ở trạng thái bản nháp."
  if (status === 400) return "Dữ liệu điều chỉnh không hợp lệ."
  if (error instanceof Error && error.message) return error.message
  return "Không thể thêm dòng điều chỉnh. Vui lòng thử lại."
}

export function AddInvoiceAdjustmentDialog({
  open,
  invoicePublicId,
  currency,
  onOpenChange,
  onChanged,
}: AddInvoiceAdjustmentDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { description: "", amount: "" },
  })

  useEffect(() => {
    if (open) form.reset({ description: "", amount: "" })
  }, [form, open])

  async function submit(values: FormValues) {
    setIsSubmitting(true)
    try {
      const invoice = await addInvoiceAdjustment(invoicePublicId, {
        description: values.description.trim(),
        amount: Number(values.amount),
      })
      onChanged(invoice)
      onOpenChange(false)
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !isSubmitting && onOpenChange(next)}>
      <DialogContent className="max-w-lg p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>Thêm dòng điều chỉnh</DialogTitle>
          <DialogDescription>
            Nhập số dương để cộng thêm hoặc số âm để giảm trừ trên hóa đơn.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)}>
            <div className="flex flex-col gap-5 px-6 py-5">
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Nội dung</FormLabel>
                    <FormControl><Textarea maxLength={200} rows={3} {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="amount"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Số tiền ({currency})</FormLabel>
                    <FormControl>
                      <Input type="number" step="0.01" inputMode="decimal" placeholder="Ví dụ: 100000 hoặc -50000" {...field} />
                    </FormControl>
                    <FormDescription>Không được bằng 0; tối đa 2 chữ số thập phân.</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {form.formState.errors.root?.message && (
                <p className="text-sm text-destructive">{form.formState.errors.root.message}</p>
              )}
            </div>
            <DialogFooter className="border-t px-6 py-4">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                Hủy
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 data-icon="inline-start" className="animate-spin" />}
                Thêm điều chỉnh
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
