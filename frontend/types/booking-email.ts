export type BookingEmailStatus = "QUEUED" | "SENDING" | "SENT" | "FAILED" | "BOUNCED"

export interface BookingEmail {
  id: number
  toEmail: string
  subject: string
  body: string
  status: BookingEmailStatus
  attemptCount: number
  scheduledAt: string | null
  sentAt: string | null
  createdAt: string
}

export interface BookingEmailRequest {
  subject: string
  body: string
}
