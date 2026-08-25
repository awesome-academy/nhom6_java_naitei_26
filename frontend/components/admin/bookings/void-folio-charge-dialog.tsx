"use client"

import { useEffect, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Loader2 } from "lucide-react"
import { useForm } from "react-hook-form"
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
import { Textarea } from "@/components/ui/textarea"
import { voidFolioCharge } from "@/lib/api/folio"
import type { FolioChargeResponse } from "@/types/booking-staff"

const formSchema = z.object({
  reason: z.string().trim()
    .min(1, "Lý do hủy là bắt buộc")
    .max(2000, "Lý do tối đa 2.000 ký tự"),
})

type FormValues = z.infer<typeof formSchema>

interface VoidFolioChargeDialogProps {
  open: boolean
  bookingPublicId: string
  charge: FolioChargeResponse | null
  onOpenChange: (open: boolean) => void
  onVoided: (charge: FolioChargeResponse) => void
}

function getErrorMessage(error: unknown): string {
  const status = (error as { status?: number })?.status
  if (status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  if (status === 403) return "Bạn không có quyền hủy khoản phát sinh."
  if (status === 400) return "Khoản này đã bị hủy hoặc booking không còn ở trạng thái đang lưu trú."
  if (status === 404) return "Không tìm thấy khoản phát sinh trong booking này."
  if (error instanceof Error && error.message) return error.message
  return "Không thể hủy khoản phát sinh. Vui lòng thử lại."
}

export function VoidFolioChargeDialog({
  open,
  bookingPublicId,
  charge,
  onOpenChange,
  onVoided,
}: VoidFolioChargeDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { reason: "" },
  })

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => form.reset({ reason: "" }), 0)
    return () => window.clearTimeout(timer)
  }, [form, open])

  async function submit(values: FormValues) {
    if (!charge) return
    setIsSubmitting(true)
    form.clearErrors("root")
    try {
      const voided = await voidFolioCharge(bookingPublicId, charge.id, {
        reason: values.reason.trim(),
      })
      onVoided(voided)
      toast.success("Đã hủy khoản phát sinh")
      onOpenChange(false)
    } catch (error) {
      form.setError("root", { message: getErrorMessage(error) })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="max-w-lg p-0">
        <DialogHeader className="border-b px-6 py-5">
          <DialogTitle>Hủy khoản phát sinh</DialogTitle>
          <DialogDescription>
            Dòng “{charge?.description ?? ""}” vẫn được lưu và hiển thị để phục vụ đối soát.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(submit)}>
            <div className="flex flex-col gap-4 px-6 py-5">
              <FormField
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Lý do hủy</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={4}
                        maxLength={2000}
                        placeholder="Ví dụ: ghi nhận trùng khoản minibar"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>Tối đa 2.000 ký tự.</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {form.formState.errors.root?.message && (
                <p className="text-sm text-[var(--destructive)]" role="alert">
                  {form.formState.errors.root.message}
                </p>
              )}
            </div>
            <DialogFooter className="border-t px-6 py-4">
              <Button type="button" variant="outline" disabled={isSubmitting} onClick={() => onOpenChange(false)}>
                Giữ lại
              </Button>
              <Button type="submit" variant="destructive" disabled={isSubmitting || !charge}>
                {isSubmitting && <Loader2 data-icon="inline-start" className="animate-spin" />}
                Xác nhận hủy
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
