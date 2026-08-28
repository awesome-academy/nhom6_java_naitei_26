"use client"

import { useEffect, useState } from "react"
import { Check, Loader2 } from "lucide-react"
import { toast } from "sonner"

import { checkInBooking } from "@/lib/api/booking-staff-api"
import type {
  BookingCheckInGuest,
  BookingCheckInRequest,
  BookingStaffDetail,
} from "@/types/booking-staff"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
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

const ID_DOCUMENT_TYPES: Array<{ value: BookingCheckInGuest["idDocumentType"]; label: string }> = [
  { value: "NATIONAL_ID", label: "CCCD" },
  { value: "PASSPORT", label: "Hộ chiếu" },
  { value: "DRIVER_LICENSE", label: "Giấy phép lái xe" },
]

type GuestForm = BookingCheckInGuest & {
  nationality: string
  dateOfBirth: string
}

type RoomForm = {
  guestCount: number
  guests: GuestForm[]
}

type RoomFormState = Record<string, RoomForm>

export function BookingCheckInDialog({
  open,
  onOpenChange,
  booking,
  onSuccess,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  booking: BookingStaffDetail
  onSuccess: () => void | Promise<void>
}) {
  const [roomForms, setRoomForms] = useState<RoomFormState>({})
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      setRoomForms(buildInitialRoomForms(booking))
      setError(null)
    }, 0)
    return () => window.clearTimeout(timer)
  }, [booking, open])

  function updateGuest(roomId: number, guestIndex: number, patch: Partial<GuestForm>) {
    setRoomForms((current) => {
      const room = current[String(roomId)]
      if (!room || !room.guests[guestIndex]) return current
      const nextGuests = [...room.guests]
      nextGuests[guestIndex] = { ...nextGuests[guestIndex], ...patch }
      return { ...current, [roomId]: { ...room, guests: nextGuests } }
    })
  }

  function updateGuestCount(roomId: number, value: string) {
    const guestCount = Math.max(1, Math.min(50, Number(value) || 1))
    setRoomForms((current) => {
      const room = current[String(roomId)]
      if (!room) return current
      const guests = room.guests.slice(0, guestCount)
      while (guests.length < guestCount) guests.push(createGuest())
      return { ...current, [roomId]: { guestCount, guests } }
    })
  }

  function validate(): string | null {
    for (const room of booking.rooms) {
      const form = roomForms[String(room.id)]
      if (!form) return `Thiếu thông tin phòng ${room.roomNumber || room.roomTypeName}`
      if (form.guests.length !== form.guestCount) {
        return `Danh sách khách phòng ${room.roomNumber || room.roomTypeName} chưa khớp số người`
      }
      for (const [index, guest] of form.guests.entries()) {
        if (!guest.fullName.trim()) return `Vui lòng nhập họ tên khách ${index + 1}`
        if (!guest.idDocumentNumber.trim()) return `Vui lòng nhập số giấy tờ khách ${index + 1}`
        if (!guest.idDocumentType) return `Vui lòng chọn loại giấy tờ khách ${index + 1}`
      }
    }
    return null
  }

  async function submit() {
    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }

    const request: BookingCheckInRequest = {
      rooms: booking.rooms.map((room) => {
        const form = roomForms[String(room.id)]
        return {
          bookingRoomId: room.id,
          guestCount: form.guestCount,
          guests: form.guests.map((guest) => ({
            fullName: guest.fullName.trim(),
            nationality: guest.nationality.trim() || undefined,
            idDocumentType: guest.idDocumentType,
            idDocumentNumber: guest.idDocumentNumber.trim(),
            dateOfBirth: guest.dateOfBirth || undefined,
          })),
        }
      }),
    }

    setIsSubmitting(true)
    setError(null)
    try {
      await checkInBooking(booking.publicId, request)
      toast.success(`Đã check-in booking ${booking.bookingCode}`)
      onOpenChange(false)
      await onSuccess()
    } catch (submitError) {
      const message = submitError instanceof Error
        ? submitError.message
        : "Không thể check-in booking"
      setError(message)
      toast.error(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !isSubmitting && onOpenChange(nextOpen)}>
      <DialogContent className="flex max-h-[92vh] max-w-4xl flex-col overflow-hidden">
        <DialogHeader>
          <DialogTitle>Check-in booking {booking.bookingCode}</DialogTitle>
          <DialogDescription>
            Nhập thông tin thực tế của khách lưu trú theo từng phòng. Mọi khách đều cần giấy tờ tùy thân.
          </DialogDescription>
        </DialogHeader>

        <div className="min-h-0 flex-1 overflow-y-auto pr-1">
          <div className="flex flex-col gap-4">
            {error && (
              <Alert variant="destructive">
                <AlertTitle>Không thể check-in</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {booking.rooms.map((room) => {
              const form = roomForms[String(room.id)]
              if (!form) return null
              return (
                <section key={room.id} className="rounded-xl border p-4">
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <h3 className="font-semibold">
                        {room.roomNumber ? `Phòng ${room.roomNumber}` : room.roomTypeName}
                      </h3>
                      <p className="text-sm text-muted-foreground">{room.roomTypeName}</p>
                    </div>
                    <Badge variant="outline">{room.guestCount} khách hiện tại</Badge>
                  </div>

                  <div className="mb-4 max-w-xs">
                    <Label htmlFor={`check-in-count-${room.id}`}>Số người lưu trú</Label>
                    <Input
                      id={`check-in-count-${room.id}`}
                      type="number"
                      min={1}
                      max={50}
                      value={form.guestCount}
                      onChange={(event) => updateGuestCount(room.id, event.target.value)}
                      className="mt-2"
                    />
                  </div>

                  <div className="flex flex-col gap-4">
                    {form.guests.map((guest, guestIndex) => (
                      <div key={`${room.id}-${guestIndex}`} className="rounded-lg bg-muted/30 p-3">
                        <div className="mb-3 flex items-center gap-2">
                          <p className="text-sm font-medium">
                            {guestIndex === 0 ? "Khách chính" : `Khách phụ ${guestIndex}`}
                          </p>
                          {guestIndex === 0 && <Badge variant="secondary">SĐT: {booking.contactPhone || "-"}</Badge>}
                        </div>
                        <div className="grid gap-3 md:grid-cols-2">
                          <div>
                            <Label htmlFor={`check-in-name-${room.id}-${guestIndex}`}>Họ tên</Label>
                            <Input
                              id={`check-in-name-${room.id}-${guestIndex}`}
                              value={guest.fullName}
                              onChange={(event) => updateGuest(room.id, guestIndex, { fullName: event.target.value })}
                              className="mt-2"
                            />
                          </div>
                          <div>
                            <Label htmlFor={`check-in-document-type-${room.id}-${guestIndex}`}>Loại giấy tờ</Label>
                            <Select
                              value={guest.idDocumentType}
                              onValueChange={(value: GuestForm["idDocumentType"]) => updateGuest(room.id, guestIndex, { idDocumentType: value })}
                            >
                              <SelectTrigger id={`check-in-document-type-${room.id}-${guestIndex}`} className="mt-2">
                                <SelectValue placeholder="Chọn loại giấy tờ" />
                              </SelectTrigger>
                              <SelectContent>
                                {ID_DOCUMENT_TYPES.map((documentType) => (
                                  <SelectItem key={documentType.value} value={documentType.value}>
                                    {documentType.label}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>
                          <div>
                            <Label htmlFor={`check-in-document-${room.id}-${guestIndex}`}>Số giấy tờ</Label>
                            <Input
                              id={`check-in-document-${room.id}-${guestIndex}`}
                              value={guest.idDocumentNumber}
                              onChange={(event) => updateGuest(room.id, guestIndex, { idDocumentNumber: event.target.value })}
                              className="mt-2"
                            />
                          </div>
                          <div>
                            <Label htmlFor={`check-in-nationality-${room.id}-${guestIndex}`}>Quốc tịch</Label>
                            <Input
                              id={`check-in-nationality-${room.id}-${guestIndex}`}
                              value={guest.nationality}
                              onChange={(event) => updateGuest(room.id, guestIndex, { nationality: event.target.value.toUpperCase() })}
                              placeholder="VN"
                              className="mt-2"
                            />
                          </div>
                          <div>
                            <Label htmlFor={`check-in-dob-${room.id}-${guestIndex}`}>Ngày sinh</Label>
                            <Input
                              id={`check-in-dob-${room.id}-${guestIndex}`}
                              type="date"
                              value={guest.dateOfBirth}
                              onChange={(event) => updateGuest(room.id, guestIndex, { dateOfBirth: event.target.value })}
                              className="mt-2"
                            />
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )
            })}
          </div>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            Hủy
          </Button>
          <Button type="button" onClick={() => void submit()} disabled={isSubmitting}>
            {isSubmitting ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Check data-icon="inline-start" />}
            Hoàn tất check-in
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function buildInitialRoomForms(booking: BookingStaffDetail): RoomFormState {
  const forms: RoomFormState = {}
  for (const room of booking.rooms) {
    const existingGuests = booking.guests.filter((guest) => guest.bookingRoomId === room.id)
    const guestCount = Math.max(1, room.guestCount || existingGuests.length || 1)
    const guests = existingGuests.slice(0, guestCount).map(toGuestForm)
    while (guests.length < guestCount) {
      guests.push(createGuest(guests.length === 0 ? booking.contactName : ""))
    }
    forms[String(room.id)] = { guestCount, guests }
  }
  return forms
}

function toGuestForm(guest: BookingStaffDetail["guests"][number]): GuestForm {
  return {
    fullName: guest.fullName,
    nationality: guest.nationality || "",
    idDocumentType: (guest.idDocumentType as GuestForm["idDocumentType"]) || "NATIONAL_ID",
    idDocumentNumber: "",
    dateOfBirth: guest.dateOfBirth || "",
  }
}

function createGuest(fullName = ""): GuestForm {
  return {
    fullName,
    nationality: "",
    idDocumentType: "NATIONAL_ID",
    idDocumentNumber: "",
    dateOfBirth: "",
  }
}
