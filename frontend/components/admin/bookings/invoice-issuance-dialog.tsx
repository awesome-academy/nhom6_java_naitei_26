"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2, Plus } from "lucide-react"
import { useForm, useWatch } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"

import { AddInvoiceAdjustmentDialog } from "@/components/admin/bookings/add-invoice-adjustment-dialog"
import { InvoicePreview } from "@/components/invoice/invoice-preview"
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
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { issueInvoice, updateInvoiceBuyer } from "@/lib/api/invoices"
import type { BookingStaffDetail } from "@/types/booking-staff"
import type { InvoiceResponse } from "@/types/invoice"

const optionalEmail = z.union([
  z.literal(""),
  z.string().email("Email không đúng định dạng").max(255, "Email tối đa 255 ký tự"),
])

const formSchema = z.object({
  buyerName: z.string().trim().min(1, "Tên người mua là bắt buộc").max(150, "Tên tối đa 150 ký tự"),
  buyerAddress: z.string().trim().max(2000, "Địa chỉ tối đa 2.000 ký tự"),
  buyerTaxCode: z.string().trim().max(20, "Mã số thuế tối đa 20 ký tự"),
  buyerEmail: optionalEmail,
})

type FormValues = z.infer<typeof formSchema>

interface InvoiceIssuanceDialogProps {
  open: boolean
  booking: BookingStaffDetail
  invoice: InvoiceResponse
  onOpenChange: (open: boolean) => void
  onChanged: (invoice: InvoiceResponse, refresh?: boolean) => void
}

function nullable(value: string): string | null {
  const normalized = value.trim()
  return normalized.length > 0 ? normalized : null
}

function getErrorMessage(error: unknown, buyerWasSaved: boolean): string {
  const status = (error as { status?: number })?.status
  const prefix = buyerWasSaved ? "Thông tin người mua đã được lưu, nhưng " : ""
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền phát hành hóa đơn."
  if (status === 404) return "Không tìm thấy hóa đơn."
  if (status === 409) return `${prefix}hóa đơn không còn ở trạng thái bản nháp.`
  if (status === 400) return `${prefix}hóa đơn chưa đủ điều kiện phát hành.`
  if (status === 503) return `${prefix}dịch vụ lưu trữ PDF đang tạm thời không khả dụng.`
  if (error instanceof Error && error.message) return `${prefix}${error.message}`
  return `${prefix}không thể phát hành hóa đơn. Vui lòng thử lại.`
}

export function InvoiceIssuanceDialog({
  open,
  booking,
  invoice,
  onOpenChange,
  onChanged,
}: InvoiceIssuanceDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isAdjustmentOpen, setIsAdjustmentOpen] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      buyerName: invoice.buyerName || booking.contactName,
      buyerAddress: invoice.buyerAddress || booking.contactAddress || "",
      buyerTaxCode: invoice.buyerTaxCode || "",
      buyerEmail: invoice.buyerEmail || booking.contactEmail || "",
    },
  })

  useEffect(() => {
    if (!open) return
    form.reset({
      buyerName: invoice.buyerName || booking.contactName,
      buyerAddress: invoice.buyerAddress || booking.contactAddress || "",
      buyerTaxCode: invoice.buyerTaxCode || "",
      buyerEmail: invoice.buyerEmail || booking.contactEmail || "",
    })
  }, [
    booking.contactAddress,
    booking.contactEmail,
    booking.contactName,
    form,
    invoice.buyerAddress,
    invoice.buyerEmail,
    invoice.buyerName,
    invoice.buyerTaxCode,
    invoice.publicId,
    open,
  ])

  const values = useWatch({ control: form.control })

  async function submit(formValues: FormValues) {
    setIsSubmitting(true)
    form.clearErrors("root")
    let buyerWasSaved = false
    try {
      const updatedDraft = await updateInvoiceBuyer(invoice.publicId, {
        buyerName: formValues.buyerName.trim(),
        buyerAddress: nullable(formValues.buyerAddress),
        buyerTaxCode: nullable(formValues.buyerTaxCode),
        buyerEmail: nullable(formValues.buyerEmail),
      })
      buyerWasSaved = true
      onChanged(updatedDraft, false)

      const issued = await issueInvoice(invoice.publicId)
      onChanged(issued, true)
      toast.success(`Đã phát hành hóa đơn ${issued.invoiceNumber ?? ""}`.trim())
      onOpenChange(false)
    } catch (error) {
      console.error("Failed to issue invoice", { invoicePublicId: invoice.publicId, error })
      form.setError("root", { message: getErrorMessage(error, buyerWasSaved) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
        <DialogContent className="flex max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] max-w-6xl flex-col gap-0 overflow-hidden p-0">
          <DialogHeader className="shrink-0 border-b px-6 py-5">
            <DialogTitle>Xuất hóa đơn</DialogTitle>
            <DialogDescription>
              Kiểm tra thông tin người mua và bản xem trước trước khi phát hành.
            </DialogDescription>
          </DialogHeader>

          <Form {...form}>
            <form onSubmit={form.handleSubmit(submit)} className="flex min-h-0 flex-1 flex-col">
              <div className="grid min-h-0 flex-1 overflow-y-auto lg:grid-cols-[minmax(280px,0.75fr)_minmax(0,1.5fr)]">
                <div className="flex flex-col gap-5 border-b px-6 py-5 lg:border-r lg:border-b-0">
                  <FormField
                    control={form.control}
                    name="buyerName"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Tên người mua</FormLabel>
                        <FormControl><Input maxLength={150} {...field} /></FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="buyerAddress"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Địa chỉ</FormLabel>
                        <FormControl><Textarea maxLength={2000} rows={4} {...field} /></FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="buyerTaxCode"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Mã số thuế</FormLabel>
                        <FormControl><Input maxLength={20} {...field} /></FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="buyerEmail"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Email</FormLabel>
                        <FormControl><Input type="email" maxLength={255} {...field} /></FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <Button type="button" variant="outline" onClick={() => setIsAdjustmentOpen(true)}>
                    <Plus data-icon="inline-start" />
                    Thêm dòng điều chỉnh
                  </Button>
                  {form.formState.errors.root?.message && (
                    <p className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                      {form.formState.errors.root.message}
                    </p>
                  )}
                </div>

                <div className="bg-muted/30 p-4 sm:p-6">
                  <InvoicePreview
                    invoice={invoice}
                    bookingCode={booking.bookingCode}
                    buyer={{
                      buyerName: values.buyerName ?? "",
                      buyerAddress: values.buyerAddress ?? "",
                      buyerTaxCode: values.buyerTaxCode ?? "",
                      buyerEmail: values.buyerEmail ?? "",
                    }}
                  />
                </div>
              </div>

              <DialogFooter className="shrink-0 border-t bg-background px-6 py-4">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                  Hủy
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting && <Loader2 data-icon="inline-start" className="animate-spin" />}
                  Phát hành
                </Button>
              </DialogFooter>
            </form>
          </Form>
        </DialogContent>
      </Dialog>

      <AddInvoiceAdjustmentDialog
        open={isAdjustmentOpen}
        invoicePublicId={invoice.publicId}
        currency={invoice.currency}
        onOpenChange={setIsAdjustmentOpen}
        onChanged={(updatedInvoice) => {
          onChanged(updatedInvoice, true)
          setIsAdjustmentOpen(false)
          toast.success("Đã thêm dòng điều chỉnh")
        }}
      />
    </>
  )
}
