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
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Textarea } from "@/components/ui/textarea"
import { voidInvoice } from "@/lib/api/invoices"
import type { InvoiceResponse } from "@/types/invoice"

const formSchema = z.object({
  reason: z.string().trim().min(1, "Lý do hủy là bắt buộc").max(2000, "Lý do tối đa 2.000 ký tự"),
})

type FormValues = z.infer<typeof formSchema>

interface VoidInvoiceDialogProps {
  open: boolean
  invoice: InvoiceResponse
  onOpenChange: (open: boolean) => void
  onVoided: (invoice: InvoiceResponse) => void
}

function getErrorMessage(error: unknown): string {
  const status = (error as { status?: number })?.status
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền hủy hóa đơn."
  if (status === 404) return "Không tìm thấy hóa đơn."
  if (status === 409) return "Hóa đơn không còn ở trạng thái đã phát hành."
  if (status === 400) return "Lý do hủy không hợp lệ."
  if (error instanceof Error && error.message) return error.message
  return "Không thể hủy hóa đơn. Vui lòng thử lại."
}

export function VoidInvoiceDialog({ open, invoice, onOpenChange, onVoided }: VoidInvoiceDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { reason: "" },
  })

  useEffect(() => {
    if (open) form.reset({ reason: "" })
  }, [form, open])

  async function submit(values: FormValues) {
    setIsSubmitting(true)
    form.clearErrors("root")
    try {
      const response = await voidInvoice(invoice.publicId, {
        reason: values.reason.trim(),
        createReplacement: false,
      })
      onVoided(response.voidedInvoice)
      onOpenChange(false)
    } catch (error) {
      console.error("Failed to void invoice", { invoicePublicId: invoice.publicId, error })
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="max-w-lg p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>Hủy hóa đơn {invoice.invoiceNumber}</DialogTitle>
          <DialogDescription>
            Hóa đơn sẽ chuyển sang trạng thái đã hủy và không tạo hóa đơn thay thế.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)}>
            <div className="px-6 py-5">
              <FormField
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Lý do hủy</FormLabel>
                    <FormControl><Textarea maxLength={2000} rows={5} {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {form.formState.errors.root?.message && (
                <p className="mt-4 text-sm text-destructive">{form.formState.errors.root.message}</p>
              )}
            </div>
            <DialogFooter className="border-t px-6 py-4">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                Quay lại
              </Button>
              <Button type="submit" variant="destructive" disabled={isSubmitting}>
                {isSubmitting && <Loader2 data-icon="inline-start" className="animate-spin" />}
                Hủy hóa đơn
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
