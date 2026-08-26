"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { addDays, differenceInCalendarDays, format, parseISO } from "date-fns"
import { ArrowLeft, ArrowRight, CalendarDays, Check, ExternalLink, Loader2, Users } from "lucide-react"
import { toast } from "sonner"

import { getBookingRoomTypes, calculateStaffBookingPrice } from "@/lib/api/booking"
import {
  createStaffBooking,
  getStaffRoomBookingMap,
  type StaffBookingGuestCreateItem,
  type StaffBookingIdDocumentType,
  type StaffBookingCreateRequest,
} from "@/lib/api/booking-staff-api"
import type { Booking, PriceCalculation } from "@/types/booking"
import type { RoomType } from "@/types/room-type"
import type { RoomBookingMapRoom, RoomTimelineEvent } from "@/types/room-booking-map"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "@/lib/utils"

const blockLabels: Record<NonNullable<RoomTimelineEvent["blockType"]>, string> = {
  MAINTENANCE: "Bảo trì",
  RENOVATION: "Cải tạo",
  OUT_OF_SERVICE: "Ngừng phục vụ",
  INTERNAL_USE: "Sử dụng nội bộ",
  DEEP_CLEANING: "Tổng vệ sinh",
}

const bookingStatusLabels: Record<string, string> = {
  PENDING: "Đang giữ",
  CONFIRMED: "Đã xác nhận",
  CHECKED_IN: "Đang ở",
}

const housekeepingLabels = {
  CLEAN: "Sạch",
  DIRTY: "Bẩn",
  CLEANING: "Đang dọn",
} as const

const bedTypeLabels: Record<string, string> = {
  SINGLE: "giường đơn",
  DOUBLE: "giường đôi",
  QUEEN: "giường queen",
  KING: "giường king",
  SOFA_BED: "sofa bed",
  BUNK: "giường tầng",
}

const documentTypeLabels: Record<StaffBookingIdDocumentType, string> = {
  NATIONAL_ID: "CCCD",
  PASSPORT: "Hộ chiếu",
  DRIVER_LICENSE: "Giấy phép lái xe",
}

type SelectedRoom = {
  room: RoomBookingMapRoom
  guestCount: number
  guests: StaffBookingGuestForm[]
}

type StaffBookingGuestForm = {
  fullName: string
  nationality: string
  idDocumentType: StaffBookingIdDocumentType
  idDocumentNumber: string
  dateOfBirth: string
}

type WizardStep = 1 | 2

interface StaffBookingCreateDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreated: (booking: Booking) => void | Promise<void>
  onOpenBooking?: (bookingPublicId: string) => void
}

export function StaffBookingCreateDialog({
  open,
  onOpenChange,
  onCreated,
  onOpenBooking,
}: StaffBookingCreateDialogProps) {
  const initialCheckIn = format(addDays(new Date(), 1), "yyyy-MM-dd")
  const initialCheckOut = format(addDays(new Date(), 2), "yyyy-MM-dd")
  const [checkInDate, setCheckInDate] = useState(initialCheckIn)
  const [checkOutDate, setCheckOutDate] = useState(initialCheckOut)
  const [contactName, setContactName] = useState("")
  const [contactEmail, setContactEmail] = useState("")
  const [contactPhone, setContactPhone] = useState("")
  const [specialRequests, setSpecialRequests] = useState("")
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [rooms, setRooms] = useState<RoomBookingMapRoom[]>([])
  const [selectedRooms, setSelectedRooms] = useState<Map<string, SelectedRoom>>(new Map())
  const [currentStep, setCurrentStep] = useState<WizardStep>(1)
  const [quote, setQuote] = useState<PriceCalculation[]>([])
  const [pricePreviews, setPricePreviews] = useState<Record<string, PriceCalculation>>({})
  const [isLoadingCatalog, setIsLoadingCatalog] = useState(false)
  const [isLoadingMap, setIsLoadingMap] = useState(false)
  const [isLoadingPrices, setIsLoadingPrices] = useState(false)
  const [isCalculating, setIsCalculating] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [priceError, setPriceError] = useState<string | null>(null)

  const roomTypeByCode = useMemo(
    () => new Map(roomTypes.map((roomType) => [roomType.code.toUpperCase(), roomType])),
    [roomTypes]
  )
  const selectedRoomItems = useMemo(() => [...selectedRooms.values()], [selectedRooms])
  const floors = useMemo(() => {
    const grouped = new Map<number | null, RoomBookingMapRoom[]>()
    rooms.forEach((room) => {
      const current = grouped.get(room.floor) ?? []
      current.push(room)
      grouped.set(room.floor, current)
    })
    return [...grouped.entries()].sort(([left], [right]) => {
      if (left === null) return 1
      if (right === null) return -1
      return left - right
    })
  }, [rooms])

  const resetForm = useCallback(() => {
    setCheckInDate(format(addDays(new Date(), 1), "yyyy-MM-dd"))
    setCheckOutDate(format(addDays(new Date(), 2), "yyyy-MM-dd"))
    setContactName("")
    setContactEmail("")
    setContactPhone("")
    setSpecialRequests("")
    setRooms([])
    setSelectedRooms(new Map())
    setCurrentStep(1)
    setQuote([])
    setPricePreviews({})
    setIsLoadingPrices(false)
    setError(null)
    setPriceError(null)
  }, [])

  const loadRoomTypes = useCallback(async () => {
    setIsLoadingCatalog(true)
    try {
      setRoomTypes(await getBookingRoomTypes())
    } catch (loadError) {
      setError(getErrorMessage(loadError, "Không thể tải loại phòng"))
    } finally {
      setIsLoadingCatalog(false)
    }
  }, [])

  const loadRoomMap = useCallback(async () => {
    setPricePreviews({})
    setIsLoadingPrices(false)
    setPriceError(null)
    if (!checkInDate || !checkOutDate || checkOutDate <= checkInDate) {
      setRooms([])
      return
    }
    setIsLoadingMap(true)
    setError(null)
    try {
      setRooms(await getStaffRoomBookingMap(checkInDate, checkOutDate))
      setSelectedRooms(new Map())
      setCurrentStep(1)
      setQuote([])
    } catch (loadError) {
      setRooms([])
      setError(getErrorMessage(loadError, "Không thể tải sơ đồ phòng"))
    } finally {
      setIsLoadingMap(false)
    }
  }, [checkInDate, checkOutDate])

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      void loadRoomTypes()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [loadRoomTypes, open])

  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(() => {
      void loadRoomMap()
    }, 0)
    return () => window.clearTimeout(timer)
  }, [loadRoomMap, open])

  useEffect(() => {
    if (!open || roomTypes.length === 0 || !checkInDate || !checkOutDate || checkOutDate <= checkInDate) {
      return
    }

    let ignore = false
    const priceOptions = roomTypes.filter((roomType) => roomType.isActive)

    const timer = window.setTimeout(() => {
      if (ignore) return
      setIsLoadingPrices(true)
      setPriceError(null)
      void Promise.all(
        priceOptions.map(async (roomType) => {
          const calculation = await calculateStaffBookingPrice({
            roomTypeCode: roomType.code,
            paymentOption: "ONLINE",
            checkInDate,
            checkOutDate,
            adults: 1,
          })
          return [roomType.code.toUpperCase(), calculation] as const
        })
      )
        .then((entries) => {
          if (!ignore) setPricePreviews(Object.fromEntries(entries))
        })
        .catch((loadError) => {
          if (!ignore) {
            setPricePreviews({})
            setPriceError(getErrorMessage(loadError, "Không thể tải giá phòng theo ngày"))
          }
        })
        .finally(() => {
          if (!ignore) setIsLoadingPrices(false)
        })
    }, 0)

    return () => {
      ignore = true
      window.clearTimeout(timer)
    }
  }, [checkInDate, checkOutDate, open, roomTypes])

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) resetForm()
    onOpenChange(nextOpen)
  }

  function selectRoom(room: RoomBookingMapRoom) {
    if (!room.selectable) return
    const roomType = room.roomTypeCode
      ? roomTypeByCode.get(room.roomTypeCode.toUpperCase())
      : undefined
    if (!roomType) {
      toast.error("Không tìm thấy thông tin loại phòng")
      return
    }

    setError(null)
    setQuote([])
    setSelectedRooms((current) => {
      const next = new Map(current)
      if (next.has(room.roomNumber)) {
        next.delete(room.roomNumber)
      } else {
        next.set(room.roomNumber, {
          room,
          guestCount: 1,
          guests: [createGuestForm(contactName.trim())],
        })
      }
      return next
    })
  }

  function updateGuest(roomNumber: string, guestIndex: number, update: Partial<StaffBookingGuestForm>) {
    setQuote([])
    setSelectedRooms((current) => {
      const existing = current.get(roomNumber)
      if (!existing || !existing.guests[guestIndex]) return current
      const next = new Map(current)
      const guests = [...existing.guests]
      guests[guestIndex] = { ...guests[guestIndex], ...update }
      next.set(roomNumber, { ...existing, guests })
      return next
    })
  }

  function updateGuestCount(roomNumber: string, guestCount: number) {
    setQuote([])
    setSelectedRooms((current) => {
      const existing = current.get(roomNumber)
      if (!existing) return current
      const guests = existing.guests.slice(0, guestCount)
      while (guests.length < guestCount) guests.push(createGuestForm())
      const next = new Map(current)
      next.set(roomNumber, { ...existing, guestCount, guests })
      return next
    })
  }

  function validateForm() {
    if (!checkInDate || !checkOutDate || checkOutDate <= checkInDate) {
      return "Ngày trả phòng phải sau ngày nhận phòng"
    }
    if (!contactName.trim()) return "Vui lòng nhập tên người liên hệ"
    if (!contactPhone.trim()) return "Vui lòng nhập số điện thoại người liên hệ"
    if (selectedRoomItems.length === 0) return "Vui lòng chọn ít nhất một phòng"
    for (const item of selectedRoomItems) {
      const roomType = item.room.roomTypeCode
        ? roomTypeByCode.get(item.room.roomTypeCode.toUpperCase())
        : undefined
      const maxGuestCount = getRoomGuestLimit(item.room, roomType)
      if (item.guests.length !== item.guestCount) {
        return `Danh sách khách phòng ${item.room.roomNumber} chưa khớp số người`
      }
      if (item.guestCount > maxGuestCount) {
        return `Số người vượt quá giới hạn phòng ${item.room.roomNumber}`
      }
      for (const [index, guest] of item.guests.entries()) {
        if (!guest.fullName.trim()) return `Vui lòng nhập họ tên khách ${index + 1} của phòng ${item.room.roomNumber}`
        if (!guest.idDocumentNumber.trim()) return `Vui lòng nhập số giấy tờ khách ${index + 1} của phòng ${item.room.roomNumber}`
        if (!guest.idDocumentType) return `Vui lòng chọn loại giấy tờ khách ${index + 1} của phòng ${item.room.roomNumber}`
      }
    }
    return null
  }

  async function calculateQuote() {
    const validationError = validateForm()
    if (validationError) {
      setError(validationError)
      return false
    }

    setIsCalculating(true)
    setError(null)
    try {
      const calculations = await Promise.all(
        selectedRoomItems.map((item) => {
          if (!item.room.roomTypeCode) {
            throw new Error(`Phòng ${item.room.roomNumber} chưa có loại phòng`)
          }
          return calculateStaffBookingPrice({
            roomTypeCode: item.room.roomTypeCode,
            paymentOption: "ONLINE",
            checkInDate,
            checkOutDate,
            adults: item.guestCount,
          })
        })
      )
      setQuote(calculations)
      return true
    } catch (quoteError) {
      setQuote([])
      setError(getErrorMessage(quoteError, "Không thể tính giá booking"))
      return false
    } finally {
      setIsCalculating(false)
    }
  }

  async function handleCreate() {
    const validationError = validateForm()
    if (validationError) {
      setError(validationError)
      return
    }
    if (quote.length !== selectedRoomItems.length) {
      await calculateQuote()
      return
    }

    const request: StaffBookingCreateRequest = {
      contactName: contactName.trim(),
      contactEmail: contactEmail.trim() || undefined,
      contactPhone: contactPhone.trim(),
      specialRequests: specialRequests.trim() || undefined,
      rooms: selectedRoomItems.map((item) => ({
        roomNumber: item.room.roomNumber,
        roomTypeCode: item.room.roomTypeCode ?? "",
        paymentOption: "ONLINE",
        checkInDate,
        checkOutDate,
        guestCount: item.guestCount,
        guests: item.guests.map((guest): StaffBookingGuestCreateItem => ({
          fullName: guest.fullName.trim(),
          nationality: guest.nationality.trim() || undefined,
          idDocumentType: guest.idDocumentType,
          idDocumentNumber: guest.idDocumentNumber.trim(),
          dateOfBirth: guest.dateOfBirth || undefined,
        })),
      })),
    }

    setIsCreating(true)
    setError(null)
    try {
      const created = await createStaffBooking(request)
      toast.success(`Đã tạo booking ${created.bookingCode}`)
      void onCreated(created)
      handleOpenChange(false)
    } catch (createError) {
      setError(getErrorMessage(createError, "Không thể tạo booking"))
    } finally {
      setIsCreating(false)
    }
  }

  function goToGuestStep() {
    if (!checkInDate || !checkOutDate || checkOutDate <= checkInDate) {
      setError("Ngày trả phòng phải sau ngày nhận phòng")
      return
    }
    if (selectedRoomItems.length === 0) {
      setError("Vui lòng chọn ít nhất một phòng")
      return
    }
    setError(null)
    setCurrentStep(2)
  }

  const totalAmount = quote.reduce((sum, calculation) => sum + Number(calculation.totalAmount), 0)

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[92vh] max-w-7xl flex-col overflow-hidden">
        <DialogHeader>
          <DialogTitle>Tạo booking cho khách</DialogTitle>
          <DialogDescription>
            {currentStep === 1
              ? "Chọn ngày và phòng CLEAN còn trống trước khi nhập thông tin khách."
              : "Nhập người liên hệ và thông tin lưu trú cho từng phòng đã chọn."}
          </DialogDescription>
        </DialogHeader>

        <div className="min-h-0 flex-1 overflow-y-auto pr-1">
          <div className="flex flex-col gap-5">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge variant={currentStep === 1 ? "default" : "secondary"}>1. Chọn phòng</Badge>
              <span className="text-muted-foreground">→</span>
              <Badge variant={currentStep === 2 ? "default" : "secondary"}>2. Thông tin khách</Badge>
            </div>

            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {currentStep === 1 ? (
              <>
                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <CalendarDays className="size-4" />
                      Khoảng ngày lưu trú
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-2">
                      <Label htmlFor="staff-booking-check-in">Nhận phòng</Label>
                      <Input
                        id="staff-booking-check-in"
                        type="date"
                        value={checkInDate}
                        onChange={(event) => setCheckInDate(event.target.value)}
                      />
                    </div>
                    <div className="flex flex-col gap-2">
                      <Label htmlFor="staff-booking-check-out">Trả phòng</Label>
                      <Input
                        id="staff-booking-check-out"
                        type="date"
                        value={checkOutDate}
                        min={checkInDate}
                        onChange={(event) => setCheckOutDate(event.target.value)}
                      />
                    </div>
                  </CardContent>
                </Card>

                <Card className="min-w-0">
                  <CardHeader className="gap-2 pb-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle className="text-base">Chọn phòng trực tiếp</CardTitle>
                      <Badge variant="outline">Đã chọn: {selectedRoomItems.length}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                      Phòng có booking hoặc lịch bảo trì trong khoảng {formatDateRange(checkInDate, checkOutDate)} sẽ bị khóa.
                    </p>
                    {priceError && (
                      <p className="text-xs text-destructive">
                        {priceError} Giá trên thẻ sẽ dùng giá cơ bản để tham khảo.
                      </p>
                    )}
                  </CardHeader>
                  <CardContent>
                    {isLoadingMap || isLoadingCatalog ? (
                      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                        {Array.from({ length: 6 }).map((_, index) => (
                          <Skeleton key={index} className="h-36 w-full rounded-xl" />
                        ))}
                      </div>
                    ) : rooms.length === 0 ? (
                      <div className="flex min-h-48 items-center justify-center rounded-lg border border-dashed text-center text-sm text-muted-foreground">
                        Không có phòng trong dữ liệu sơ đồ.
                      </div>
                    ) : (
                      <div className="flex flex-col gap-4">
                        <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
                          <span>Nhấn phòng CLEAN để chọn.</span>
                          <span>•</span>
                          <span>Nhấn mã booking để mở chi tiết.</span>
                        </div>
                        {floors.map(([floor, floorRooms]) => (
                          <div key={floor ?? "unassigned"} className="flex flex-col gap-2">
                            <div className="flex items-center justify-between">
                              <h3 className="font-medium">{floor === null ? "Chưa gán tầng" : `Tầng ${floor}`}</h3>
                              <span className="text-xs text-muted-foreground">{floorRooms.length} phòng</span>
                            </div>
                            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                              {floorRooms.map((room) => {
                                const selected = selectedRooms.has(room.roomNumber)
                                const roomType = room.roomTypeCode
                                  ? roomTypeByCode.get(room.roomTypeCode.toUpperCase())
                                  : undefined
                                const pricePreview = roomType
                                  ? getRoomPricePreview(roomType, pricePreviews, getStayNights(checkInDate, checkOutDate))
                                  : null
                                return (
                                  <Card
                                    key={room.roomNumber}
                                    className={cn(
                                      "min-w-0 transition-colors",
                                      selected && "border-primary bg-primary/5 ring-1 ring-primary",
                                      !room.selectable && "bg-muted/50"
                                    )}
                                  >
                                    <CardContent className="flex flex-col gap-2 p-3">
                                      <button
                                        type="button"
                                        disabled={!room.selectable}
                                        aria-pressed={selected}
                                        onClick={() => selectRoom(room)}
                                        className={cn(
                                          "flex w-full flex-col gap-2 text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                                          !room.selectable && "cursor-not-allowed"
                                        )}
                                      >
                                        <div className="flex items-start justify-between gap-2">
                                          <div>
                                            <p className="text-lg font-semibold">{room.roomNumber}</p>
                                            <p className="truncate text-xs text-muted-foreground">
                                              {room.roomTypeName ?? room.roomTypeCode ?? "Chưa có loại phòng"}
                                            </p>
                                          </div>
                                          {selected ? <Check className="size-5 text-primary" /> : <Badge variant="outline">{housekeepingLabels[room.housekeepingStatus]}</Badge>}
                                        </div>
                                        <div className="flex flex-wrap gap-1">
                                          <Badge variant={room.selectable ? "success" : "secondary"}>
                                            {room.selectable ? "Có thể chọn" : room.unavailableReason ?? "Không thể chọn"}
                                          </Badge>
                                          {room.operationalStatus !== "ACTIVE" && (
                                            <Badge variant="destructive">{room.operationalStatus}</Badge>
                                          )}
                                        </div>
                                        {roomType && (
                                          <div className="flex flex-col gap-1 border-t pt-2 text-xs">
                                            <div className="flex items-baseline justify-between gap-2">
                                              <span className="text-muted-foreground">Giá phòng/đêm (TB)</span>
                                              <span className="font-semibold text-foreground">
                                                {pricePreview
                                                  ? formatMoney(pricePreview.nightlyPrice, pricePreview.currency)
                                                  : isLoadingPrices
                                                    ? "Đang tải..."
                                                    : "Chưa có giá"}
                                              </span>
                                            </div>
                                            <div className="flex items-baseline justify-between gap-2">
                                              <span className="text-muted-foreground">Tổng {getStayNights(checkInDate, checkOutDate)} đêm</span>
                                              <span className="font-semibold text-foreground">
                                                {pricePreview ? formatMoney(pricePreview.totalPrice, pricePreview.currency) : "-"}
                                              </span>
                                            </div>
                                            <div className="truncate text-muted-foreground">
                                              {getBedSummary(roomType)} · {room.maxOccupancy ?? roomType.maxOccupancy} người tối đa
                                            </div>
                                            <div className="truncate text-muted-foreground">
                                              Chính sách mặc định: Không hoàn tiền
                                            </div>
                                            {pricePreview?.calculation && hasVariableDailyPrices(pricePreview.calculation) && (
                                              <div className="text-muted-foreground">
                                                Giá theo ngày: {formatMoney(pricePreview.minDailyPrice, pricePreview.currency)} – {formatMoney(pricePreview.maxDailyPrice, pricePreview.currency)}/đêm
                                              </div>
                                            )}
                                          </div>
                                        )}
                                      </button>
                                      {room.timeline.length > 0 && (
                                        <div className="flex flex-col gap-1 border-t pt-2">
                                          {room.timeline.map((event) => (
                                            <TimelineEventRow
                                              key={`${event.type}-${event.startDate}-${event.endDate}-${event.bookingCode ?? event.blockType}`}
                                              event={event}
                                              onOpenBooking={onOpenBooking ? () => {
                                                if (event.bookingPublicId) {
                                                  handleOpenChange(false)
                                                  onOpenBooking(event.bookingPublicId)
                                                }
                                              } : undefined}
                                            />
                                          ))}
                                        </div>
                                      )}
                                    </CardContent>
                                  </Card>
                                )
                              })}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </>
            ) : (
              <>
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/30 px-4 py-3 text-sm">
                  <div className="flex items-center gap-2">
                    <CalendarDays className="size-4 text-muted-foreground" />
                    <span>
                      {formatDateRange(checkInDate, checkOutDate)} · {selectedRoomItems.length} phòng đã chọn
                    </span>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => setCurrentStep(1)}>
                    <ArrowLeft data-icon="inline-start" />
                    Đổi phòng
                  </Button>
                </div>

                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <Users className="size-4" />
                      Thông tin người liên hệ
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-4">
                    <div className="grid gap-4 md:grid-cols-3">
                      <div className="flex flex-col gap-2">
                        <Label htmlFor="staff-booking-contact-name">Họ tên người liên hệ</Label>
                        <Input
                          id="staff-booking-contact-name"
                          value={contactName}
                          onChange={(event) => setContactName(event.target.value)}
                          placeholder="Nguyễn Văn A"
                        />
                      </div>
                      <div className="flex flex-col gap-2">
                        <Label htmlFor="staff-booking-contact-phone">Số điện thoại <span className="text-destructive">*</span></Label>
                        <Input
                          id="staff-booking-contact-phone"
                          value={contactPhone}
                          onChange={(event) => setContactPhone(event.target.value)}
                          placeholder="0900000000"
                        />
                      </div>
                      <div className="flex flex-col gap-2">
                        <Label htmlFor="staff-booking-contact-email">Email</Label>
                        <Input
                          id="staff-booking-contact-email"
                          type="email"
                          value={contactEmail}
                          onChange={(event) => setContactEmail(event.target.value)}
                          placeholder="guest@example.com"
                        />
                      </div>
                    </div>
                    <div className="flex flex-col gap-2">
                      <Label htmlFor="staff-booking-special-requests">Ghi chú</Label>
                      <Textarea
                        id="staff-booking-special-requests"
                        value={specialRequests}
                        onChange={(event) => setSpecialRequests(event.target.value)}
                        maxLength={2000}
                        showCount
                        placeholder="Yêu cầu đặc biệt của khách"
                      />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Số điện thoại của người liên hệ là bắt buộc và được dùng cho booking. Mỗi khách lưu trú cần có giấy tờ tùy thân.
                    </p>
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">Thông tin khách lưu trú</CardTitle>
                    <p className="text-sm text-muted-foreground">
                      Khách đầu tiên là người đứng tên phòng. Các khách phụ chỉ bắt buộc họ tên và số giấy tờ.
                    </p>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-5">
                    {selectedRoomItems.map((item, index) => {
                      const roomType = item.room.roomTypeCode
                        ? roomTypeByCode.get(item.room.roomTypeCode.toUpperCase())
                        : undefined
                      const calculation = quote[index]
                      const pricePreview = roomType
                        ? getRoomPricePreview(roomType, pricePreviews, getStayNights(checkInDate, checkOutDate))
                        : null
                      const nightlyPrice = calculation ? getEffectiveNightlyPrice(calculation) : pricePreview?.nightlyPrice
                      const totalPrice = calculation ? Number(calculation.totalAmount) : pricePreview?.totalPrice
                      const currency = calculation?.currency ?? pricePreview?.currency ?? roomType?.currency ?? "VND"
                      const maxGuestCount = getRoomGuestLimit(item.room, roomType)
                      return (
                        <div key={item.room.roomNumber} className="flex flex-col gap-4 rounded-lg border p-4">
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div>
                              <p className="font-medium">Phòng {item.room.roomNumber}</p>
                              <p className="text-xs text-muted-foreground">
                                {item.room.roomTypeName ?? item.room.roomTypeCode ?? "Chưa có loại phòng"}
                                {roomType ? ` · ${getBedSummary(roomType)}` : ""}
                              </p>
                            </div>
                            <Badge variant="outline">Tối đa {item.room.maxOccupancy ?? roomType?.maxOccupancy ?? "-"} người</Badge>
                          </div>

                          <div className="grid gap-3 md:grid-cols-[minmax(160px,0.4fr)_minmax(0,1fr)]">
                            <div className="flex flex-col gap-2">
                              <Label htmlFor={`staff-guest-count-${item.room.roomNumber}`}>Số người lưu trú</Label>
                              <Input
                                id={`staff-guest-count-${item.room.roomNumber}`}
                                type="number"
                                min={1}
                                max={maxGuestCount}
                                value={item.guestCount}
                                onChange={(event) => updateGuestCount(
                                  item.room.roomNumber,
                                  Math.max(1, Number(event.target.value) || 1)
                                )}
                              />
                              <p className="text-xs text-muted-foreground">Hiện tại toàn bộ được tính là người lớn, tối đa {maxGuestCount} người.</p>
                            </div>
                          </div>

                          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                            {item.guests.map((guest, guestIndex) => (
                              <div key={`${item.room.roomNumber}-${guestIndex}`} className="flex flex-col gap-3 rounded-md bg-muted/30 p-3">
                                <div className="flex items-center justify-between gap-2">
                                  <p className="text-sm font-medium">
                                    {guestIndex === 0 ? "Khách chính / người đứng tên" : `Khách phụ ${guestIndex}`}
                                  </p>
                                  {guestIndex === 0 && <Badge variant="secondary">Bắt buộc SĐT</Badge>}
                                </div>
                                <div className="flex flex-col gap-2">
                                  <Label htmlFor={`staff-guest-name-${item.room.roomNumber}-${guestIndex}`}>Họ tên</Label>
                                  <Input
                                    id={`staff-guest-name-${item.room.roomNumber}-${guestIndex}`}
                                    value={guest.fullName}
                                    onChange={(event) => updateGuest(item.room.roomNumber, guestIndex, { fullName: event.target.value })}
                                    placeholder="Nguyễn Văn A"
                                  />
                                </div>
                                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
                                  <div className="flex flex-col gap-2">
                                    <Label htmlFor={`staff-guest-document-type-${item.room.roomNumber}-${guestIndex}`}>Loại giấy tờ</Label>
                                    <Select
                                      value={guest.idDocumentType}
                                      onValueChange={(value: StaffBookingIdDocumentType) => updateGuest(item.room.roomNumber, guestIndex, { idDocumentType: value })}
                                    >
                                      <SelectTrigger id={`staff-guest-document-type-${item.room.roomNumber}-${guestIndex}`}>
                                        <SelectValue />
                                      </SelectTrigger>
                                      <SelectContent>
                                        <SelectGroup>
                                          {Object.entries(documentTypeLabels).map(([value, label]) => (
                                            <SelectItem key={value} value={value}>{label}</SelectItem>
                                          ))}
                                        </SelectGroup>
                                      </SelectContent>
                                    </Select>
                                  </div>
                                  <div className="flex flex-col gap-2">
                                    <Label htmlFor={`staff-guest-document-${item.room.roomNumber}-${guestIndex}`}>Số giấy tờ</Label>
                                    <Input
                                      id={`staff-guest-document-${item.room.roomNumber}-${guestIndex}`}
                                      value={guest.idDocumentNumber}
                                      onChange={(event) => updateGuest(item.room.roomNumber, guestIndex, { idDocumentNumber: event.target.value })}
                                      placeholder={guest.idDocumentType === "NATIONAL_ID" ? "012345678901" : "Số giấy tờ"}
                                    />
                                  </div>
                                </div>
                                <div className="grid gap-3 sm:grid-cols-2">
                                  <div className="flex flex-col gap-2">
                                    <Label htmlFor={`staff-guest-nationality-${item.room.roomNumber}-${guestIndex}`}>Quốc tịch</Label>
                                    <Input
                                      id={`staff-guest-nationality-${item.room.roomNumber}-${guestIndex}`}
                                      value={guest.nationality}
                                      onChange={(event) => updateGuest(item.room.roomNumber, guestIndex, { nationality: event.target.value.toUpperCase() })}
                                      placeholder="VN"
                                      maxLength={2}
                                    />
                                  </div>
                                  <div className="flex flex-col gap-2">
                                    <Label htmlFor={`staff-guest-dob-${item.room.roomNumber}-${guestIndex}`}>Ngày sinh</Label>
                                    <Input
                                      id={`staff-guest-dob-${item.room.roomNumber}-${guestIndex}`}
                                      type="date"
                                      value={guest.dateOfBirth}
                                      onChange={(event) => updateGuest(item.room.roomNumber, guestIndex, { dateOfBirth: event.target.value })}
                                    />
                                  </div>
                                </div>
                                {guestIndex === 0 && (
                                  <p className="text-xs text-muted-foreground">Số điện thoại của khách chính nhập ở phần người liên hệ phía trên.</p>
                                )}
                              </div>
                            ))}
                          </div>

                          <div className="rounded-md bg-muted/50 px-3 py-2 text-xs">
                            <div className="flex items-baseline justify-between gap-2">
                              <span className="text-muted-foreground">{calculation ? "Giá theo lựa chọn" : "Giá tham khảo"}</span>
                              <span className="font-semibold text-foreground">
                                {nightlyPrice !== undefined ? `${formatMoney(nightlyPrice, currency)}/đêm` : "Chưa có giá"}
                              </span>
                            </div>
                            <div className="mt-1 flex items-baseline justify-between gap-2">
                              <span className="text-muted-foreground">Tổng lưu trú</span>
                              <span className="font-semibold text-foreground">
                                {totalPrice !== undefined ? formatMoney(totalPrice, currency) : "-"}
                              </span>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </CardContent>
                </Card>

                {quote.length > 0 && (
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base">Tóm tắt giá</CardTitle>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-2 text-sm">
                      {quote.map((calculation, index) => (
                        <div key={`${calculation.roomTypeCode}-${index}`} className="flex justify-between gap-3">
                          <span>Phòng {selectedRoomItems[index]?.room.roomNumber ?? calculation.roomTypeCode}</span>
                          <span>{formatMoney(calculation.totalAmount, calculation.currency)}</span>
                        </div>
                      ))}
                      <Separator />
                      <div className="flex justify-between gap-3 font-semibold">
                        <span>Tổng tạm tính</span>
                        <span>{formatMoney(totalAmount, quote[0]?.currency ?? "VND")}</span>
                      </div>
                      <p className="text-xs text-muted-foreground">Booking sẽ được tạo ở trạng thái PENDING và giữ phòng trong thời gian hold.</p>
                    </CardContent>
                  </Card>
                )}
              </>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)} disabled={isCreating}>
            Hủy
          </Button>
          {currentStep === 1 ? (
            <Button onClick={goToGuestStep} disabled={isLoadingMap || isLoadingCatalog || isCreating}>
              <ArrowRight data-icon="inline-start" />
              Tiếp tục nhập thông tin khách
            </Button>
          ) : quote.length === selectedRoomItems.length && quote.length > 0 ? (
            <>
              <Button variant="outline" onClick={() => setCurrentStep(1)} disabled={isCreating || isCalculating}>
                <ArrowLeft data-icon="inline-start" />
                Quay lại
              </Button>
              <Button onClick={() => void handleCreate()} disabled={isCreating || isCalculating}>
                {isCreating ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Check data-icon="inline-start" />}
                Tạo booking
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={() => setCurrentStep(1)} disabled={isCreating || isCalculating}>
                <ArrowLeft data-icon="inline-start" />
                Quay lại
              </Button>
              <Button onClick={() => void calculateQuote()} disabled={isCalculating || isCreating}>
                {isCalculating ? <Loader2 data-icon="inline-start" className="animate-spin" /> : null}
                Tính giá
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function TimelineEventRow({
  event,
  onOpenBooking,
}: {
  event: RoomTimelineEvent
  onOpenBooking?: () => void
}) {
  const isBooking = event.type === "BOOKING"
  const label = isBooking
    ? `${event.bookingCode ?? event.label} · ${bookingStatusLabels[event.bookingStatus ?? ""] ?? "Booking"}`
    : `${blockLabels[event.blockType ?? "MAINTENANCE"]} · ${event.reason ?? "Đã khóa phòng"}`

  return (
    <div className="flex items-start gap-2 text-[11px] text-muted-foreground">
      <span className={cn("mt-1 size-2 shrink-0 rounded-full", isBooking ? "bg-primary" : "bg-destructive")} />
      <div className="min-w-0 flex-1">
        <p className="truncate">{label}</p>
        <p>{formatDateRange(event.startDate, event.endDate)}</p>
      </div>
      {isBooking && onOpenBooking && event.bookingPublicId && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          aria-label={`Mở booking ${event.bookingCode ?? ""}`}
          title="Mở chi tiết booking"
          onClick={onOpenBooking}
        >
          <ExternalLink data-icon="inline-start" />
        </Button>
      )}
    </div>
  )
}

function formatDateRange(startDate: string, endDate: string) {
  if (!startDate || !endDate) return ""
  return `${format(parseISO(startDate), "dd/MM")} → ${format(parseISO(endDate), "dd/MM")}`
}

function getStayNights(startDate: string, endDate: string) {
  if (!startDate || !endDate) return 0
  return Math.max(0, differenceInCalendarDays(parseISO(endDate), parseISO(startDate)))
}

function getBedSummary(roomType: RoomType) {
  if (roomType.beds.length === 0) return `${roomType.bedCount} giường`
  return roomType.beds
    .map((bed) => `${bed.quantity} ${bedTypeLabels[bed.bedType] ?? bed.bedType}`)
    .join(", ")
}

function createGuestForm(fullName = ""): StaffBookingGuestForm {
  return {
    fullName,
    nationality: "",
    idDocumentType: "NATIONAL_ID",
    idDocumentNumber: "",
    dateOfBirth: "",
  }
}

function getRoomGuestLimit(room: RoomBookingMapRoom, roomType?: RoomType) {
  const occupancyLimit = room.maxOccupancy ?? roomType?.maxOccupancy ?? Number.MAX_SAFE_INTEGER
  const adultLimit = roomType?.maxAdults ?? Number.MAX_SAFE_INTEGER
  return Math.min(occupancyLimit, adultLimit)
}

function getEffectiveNightlyPrice(calculation: PriceCalculation) {
  return Number(calculation.roomsTotal) / Math.max(1, calculation.nights)
}

function getRoomPricePreview(
  roomType: RoomType,
  pricePreviews: Record<string, PriceCalculation>,
  nights: number
) {
  const calculation = pricePreviews[roomType.code.toUpperCase()]
  const nightlyPrice = calculation
    ? getEffectiveNightlyPrice(calculation)
    : Number(roomType.basePrice)
  const dailyPrices = calculation?.dailyRates.map((dailyRate) => Number(dailyRate.price)) ?? []

  return {
    calculation,
    nightlyPrice,
    totalPrice: calculation ? Number(calculation.totalAmount) : nightlyPrice * Math.max(1, nights),
    currency: calculation?.currency ?? roomType.currency,
    minDailyPrice: dailyPrices.length > 0 ? Math.min(...dailyPrices) : nightlyPrice,
    maxDailyPrice: dailyPrices.length > 0 ? Math.max(...dailyPrices) : nightlyPrice,
  }
}

function hasVariableDailyPrices(calculation: PriceCalculation) {
  const dailyPrices = calculation.dailyRates.map((dailyRate) => Number(dailyRate.price))
  return dailyPrices.length > 1 && Math.min(...dailyPrices) !== Math.max(...dailyPrices)
}

function formatMoney(value: number | string, currency: string) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(Number(value) || 0)
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
