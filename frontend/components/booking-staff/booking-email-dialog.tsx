"use client"

import { useCallback, useState } from "react"
import { Loader2, Mail, Send } from "lucide-react"
import { toast } from "sonner"

import { getBookingEmails, sendBookingEmail } from "@/lib/api/booking-emails"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import type { BookingEmail, BookingEmailStatus } from "@/types/booking-email"

const STATUS_LABELS: Record<BookingEmailStatus, string> = {
  QUEUED: "Đang chờ gửi",
  SENDING: "Đang gửi",
  SENT: "Đã gửi",
  FAILED: "Gửi thất bại",
  BOUNCED: "Bị trả lại",
}

function getStatusVariant(status: BookingEmailStatus) {
  if (status === "SENT") return "success" as const
  if (status === "FAILED" || status === "BOUNCED") return "destructive" as const
  return "pending" as const
}

function formatDateTime(value: string | null) {
  if (!value) return "—"
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value))
}

function getErrorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : "Không thể gửi email. Vui lòng thử lại."
}

interface BookingEmailDialogProps {
  bookingPublicId: string
  contactEmail: string | null
  canSend: boolean
}

export function BookingEmailDialog({ bookingPublicId, contactEmail, canSend }: BookingEmailDialogProps) {
  const [open, setOpen] = useState(false)
  const [subject, setSubject] = useState("")
  const [body, setBody] = useState("")
  const [history, setHistory] = useState<BookingEmail[]>([])
  const [isLoadingHistory, setIsLoadingHistory] = useState(false)
  const [isSending, setIsSending] = useState(false)

  const loadHistory = useCallback(async () => {
    if (!canSend) return
    setIsLoadingHistory(true)
    try {
      setHistory(await getBookingEmails(bookingPublicId))
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsLoadingHistory(false)
    }
  }, [bookingPublicId, canSend])

  async function handleSendEmail() {
    const normalizedSubject = subject.trim()
    const normalizedBody = body.trim()
    if (!normalizedSubject || !normalizedBody) {
      toast.error("Vui lòng nhập tiêu đề và nội dung email.")
      return
    }
    setIsSending(true)
    try {
      const queuedEmail = await sendBookingEmail(bookingPublicId, {
        subject: normalizedSubject,
        body: normalizedBody,
      })
      setHistory((current) => [queuedEmail, ...current])
      setSubject("")
      setBody("")
      toast.success("Email đã được đưa vào hàng đợi gửi.")
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setIsSending(false)
    }
  }

  if (!canSend) return null

  return (
    <>
      <Button size="sm" variant="outline" onClick={() => { setOpen(true); void loadHistory() }} disabled={!contactEmail}>
        <Mail data-icon="inline-start" /> Gửi email
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Gửi email cho khách</DialogTitle>
            <DialogDescription>
              Email sẽ được gửi đến {contactEmail || "email liên hệ của booking"} và được xử lý qua hàng đợi hệ thống.
            </DialogDescription>
          </DialogHeader>
          {!contactEmail ? (
            <p className="text-sm text-destructive">Booking này chưa có email liên hệ để gửi.</p>
          ) : (
            <div className="flex max-h-[70vh] flex-col gap-4 overflow-y-auto pr-1">
              <div className="flex flex-col gap-2">
                <Label htmlFor="booking-email-subject">Tiêu đề</Label>
                <Input
                  id="booking-email-subject"
                  value={subject}
                  maxLength={300}
                  onChange={(event) => setSubject(event.target.value)}
                  placeholder="Ví dụ: Thông tin lưu trú của quý khách"
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="booking-email-body">Nội dung</Label>
                <Textarea
                  id="booking-email-body"
                  value={body}
                  maxLength={10000}
                  showCount
                  onChange={(event) => setBody(event.target.value)}
                  placeholder="Nhập nội dung email..."
                  className="min-h-36"
                />
              </div>
              <Separator />
              <div className="flex flex-col gap-3">
                <div>
                  <h3 className="text-sm font-semibold">Lịch sử email</h3>
                  <p className="text-xs text-muted-foreground">Tối đa 20 email gần nhất của booking.</p>
                </div>
                {isLoadingHistory ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="animate-spin" /> Đang tải lịch sử...</div>
                ) : history.length === 0 ? (
                  <p className="text-sm text-muted-foreground">Chưa có email nào được gửi cho booking này.</p>
                ) : (
                  <div className="flex flex-col gap-2">
                    {history.map((email) => (
                      <div key={email.id} className="flex flex-col gap-1 rounded-md border p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="font-medium">{email.subject}</span>
                          <Badge variant={getStatusVariant(email.status)}>{STATUS_LABELS[email.status]}</Badge>
                        </div>
                        <p className="line-clamp-2 whitespace-pre-wrap text-xs text-muted-foreground">{email.body}</p>
                        <p className="text-xs text-muted-foreground">{formatDateTime(email.sentAt || email.scheduledAt || email.createdAt)}</p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={isSending}>Đóng</Button>
            <Button onClick={() => void handleSendEmail()} disabled={!contactEmail || isSending}>
              {isSending ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Send data-icon="inline-start" />}
              Gửi email
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
