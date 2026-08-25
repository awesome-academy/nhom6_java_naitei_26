"use client"

import {
  useEffect,
  useCallback,
  useMemo,
  useState,
  type ComponentType,
  type ReactNode,
} from "react"
import Link from "next/link"
import {
  addDays,
  addMonths,
  differenceInCalendarDays,
  format,
  parseISO,
  startOfMonth,
  startOfToday,
} from "date-fns"
import {
  Ban,
  BedDouble,
  BellRing,
  CalendarDays,
  Check,
  CheckCircle2,
  CreditCard,
  Heart,
  ImageIcon,
  Info,
  Loader2,
  MapPin,
  Minus,
  Plane,
  Plus,
  ReceiptText,
  Search,
  Share2,
  ShieldCheck,
  Star,
  UserRound,
  Train,
  Users,
  Wifi,
} from "lucide-react"
import { toast } from "sonner"

import { SiteHeader } from "@/components/auth/site-header"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import {
  Card,
  CardContent,
} from "@/components/ui/card"
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
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import {
  calculateBookingPrice,
  createBooking,
  getAvailability,
  getBookingRoomTypes,
} from "@/lib/api/booking"
import { useAuth } from "@/lib/auth-context"
import { cn } from "@/lib/utils"
import type {
  Booking,
  BookingRoomItem,
  PriceCalculation,
} from "@/types/booking"
import type { RoomType, RoomTypeBookingOption } from "@/types/room-type"

type SearchState = {
  checkInDate: string
  checkOutDate: string
  rooms: number
  adults: number
  children: number
}

type ContactState = {
  contactName: string
  contactEmail: string
  contactPhone: string
  specialRequests: string
}

type RoomTypeOption = {
  roomType: RoomType
  availableCount: number
}

type SelectedBookingOption = {
  selectionId: string
  roomTypeCode: string
  optionKey: string
  paymentOption: "ONLINE" | "PAY_AT_HOTEL"
  cancellationPolicyCode: string
}

type RoomCheckoutDetail = {
  selectionId: string
  guestFullName: string
}

type Quote = {
  calculations: PriceCalculation[]
  roomsTotal: number
  taxTotal: number
  totalAmount: number
  currency: string
}

type GalleryItem = {
  id: string
  url: string
  alt: string
  sortOrder: number
}

type CheapestBookingOffer = {
  roomType: RoomType
  option: RoomTypeBookingOption
  unitPrice: number
}

type CheckoutSummaryLine = {
  key: string
  roomTypeCode: string
  roomTypeName: string
  cancellationPolicyName: string
  paymentLabel: string
  roomCount: number
  nights: number
  bedLabels: string[]
  maxAdults: number | null
  roomsTotal: number
  taxTotal: number
  totalAmount: number
  currency: string
  dailyRates: { date: string; price: number }[]
}

const today = startOfToday()
const initialSearch: SearchState = {
  checkInDate: format(addDays(today, 1), "yyyy-MM-dd"),
  checkOutDate: format(addDays(today, 2), "yyyy-MM-dd"),
  rooms: 1,
  adults: 2,
  children: 0,
}

const bedTypeLabel: Record<string, string> = {
  SINGLE: "giường đơn",
  DOUBLE: "giường đôi",
  QUEEN: "giường queen",
  KING: "giường king",
  SOFA_BED: "sofa bed",
  BUNK: "giường tầng",
}

function money(value: number | string, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency,
    maximumFractionDigits: currency === "VND" ? 0 : 2,
  }).format(Number(value) || 0)
}

function compactMoney(value: number | string) {
  const numberValue = Number(value) || 0
  if (numberValue >= 1_000_000) return `${(numberValue / 1_000_000).toFixed(1)} tr`
  if (numberValue >= 1_000) return `${Math.round(numberValue / 1_000)} ngh`
  return `${numberValue}`
}

function displayDate(value: string) {
  return format(parseISO(value), "dd/MM/yyyy")
}

function mapAvailabilityByRoomTypeCode(
  availability: Record<string, number[]>,
  roomTypes: RoomType[]
) {
  const roomTypeCodeById = new Map(
    roomTypes.map((roomType) => [String(roomType.roomTypeId), roomType.code])
  )
  return Object.fromEntries(
    Object.entries(availability)
      .map(([roomTypeId, roomIds]) => [roomTypeCodeById.get(roomTypeId), roomIds.length] as const)
      .filter((entry): entry is [string, number] => Boolean(entry[0]))
  )
}

function headerDate(value: string) {
  return format(parseISO(value), "EEE, dd 'thg' M")
}

function getNights(search: SearchState) {
  return Math.max(
    0,
    differenceInCalendarDays(
      parseISO(search.checkOutDate),
      parseISO(search.checkInDate)
    )
  )
}

function getBedSummary(roomType: RoomType) {
  return getBedLabels(roomType).join(", ")
}

function getBedLabels(roomType: RoomType) {
  if (roomType.beds.length === 0) return [`${roomType.bedCount} giường`]
  return roomType.beds
    .map((bed) => `${bed.quantity} ${bedTypeLabel[bed.bedType] ?? bed.bedType}`)
}

function getAccessMessage(permissions: string[]) {
  const missing = []
  if (!permissions.includes("room:read")) missing.push("room:read")
  if (!permissions.includes("booking:create")) missing.push("booking:create")
  if (missing.length === 0) return null
  return `Tài khoản đang thiếu permission ${missing.join(", ")}. Nếu vừa chỉnh database, hãy đăng xuất rồi đăng nhập lại để refresh token/quyền.`
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) return fallback

  const apiError = error as Error & {
    status?: number
    endpoint?: string
  }

  if (apiError.status === 403) {
    const endpoint = apiError.endpoint ? ` Endpoint bị chặn: ${apiError.endpoint}.` : ""
    return `${fallback}: tài khoản không có quyền ở backend.${endpoint} Kiểm tra role CUSTOMER có room:read và booking:create.`
  }

  return apiError.message
}

function getBookingOptionUnitPrice(roomType: RoomType, option: RoomTypeBookingOption) {
  return Number(roomType.basePrice) * (1 + Number(option.priceAdjustmentPercent) / 100)
}

function sortBookingOptionsByPrice(roomType: RoomType) {
  return [...roomType.bookingOptions].sort((left, right) => {
    const priceDiff = getBookingOptionUnitPrice(roomType, left) - getBookingOptionUnitPrice(roomType, right)
    if (priceDiff !== 0) return priceDiff
    return left.optionKey.localeCompare(right.optionKey)
  })
}

function getSelectionCount(
  selections: SelectedBookingOption[],
  roomTypeCode: string,
  optionKey?: string
) {
  return selections.filter((selection) => {
    if (selection.roomTypeCode !== roomTypeCode) return false
    return optionKey ? selection.optionKey === optionKey : true
  }).length
}

function roomTypeMatchesGuestSearch(roomType: RoomType, search: SearchState) {
  const requestedGuests = search.adults + search.children
  return (
    roomType.maxOccupancy >= requestedGuests &&
    roomType.maxAdults >= search.adults &&
    roomType.maxChildren >= search.children
  )
}

function createDefaultRoomDetail(selectionId: string): RoomCheckoutDetail {
  return {
    selectionId,
    guestFullName: "",
  }
}

function getRoomDetail(
  details: Record<string, RoomCheckoutDetail>,
  selectionId: string
) {
  return details[selectionId] ?? createDefaultRoomDetail(selectionId)
}

function getSelectedEstimateTotal(
  selections: SelectedBookingOption[],
  roomTypeByCode: Map<string, RoomType>,
  nights: number
) {
  return selections.reduce((sum, selection) => {
    const roomType = roomTypeByCode.get(selection.roomTypeCode)
    const option = roomType?.bookingOptions.find((candidate) => candidate.optionKey === selection.optionKey)
    if (!roomType || !option) return sum
    return sum + getBookingOptionUnitPrice(roomType, option) * Math.max(1, nights)
  }, 0)
}

function getSelectedMaxAdults(
  selections: SelectedBookingOption[],
  roomTypeByCode: Map<string, RoomType>
) {
  return selections.reduce((sum, selection) => {
    const roomType = roomTypeByCode.get(selection.roomTypeCode)
    return sum + (roomType ? Math.min(roomType.maxAdults, roomType.maxOccupancy) : 0)
  }, 0)
}

function distributeExpectedAdults(
  selections: SelectedBookingOption[],
  guestCount: number,
  roomTypeByCode: Map<string, RoomType>
) {
  if (selections.length === 0) {
    return { allocations: [] as number[], error: "" }
  }

  const roomCapacities = selections.map((selection) => {
    const roomType = roomTypeByCode.get(selection.roomTypeCode)
    return {
      roomName: roomType?.name ?? selection.roomTypeCode,
      capacity: roomType ? Math.min(roomType.maxAdults, roomType.maxOccupancy) : 0,
    }
  })

  const invalidRoom = roomCapacities.find((room) => room.capacity < 1)
  if (invalidRoom) {
    return {
      allocations: [] as number[],
      error: `Không thể xếp khách cho ${invalidRoom.roomName}.`,
    }
  }

  const allocations = Array.from({ length: selections.length }, () => 1)
  let remainingGuests = Math.max(guestCount, selections.length) - selections.length

  for (let index = 0; index < allocations.length && remainingGuests > 0; index += 1) {
    const availableSlots = roomCapacities[index].capacity - allocations[index]
    const addedGuests = Math.min(availableSlots, remainingGuests)
    allocations[index] += addedGuests
    remainingGuests -= addedGuests
  }

  if (remainingGuests > 0) {
    const totalCapacity = roomCapacities.reduce((sum, room) => sum + room.capacity, 0)
    return {
      allocations: [] as number[],
      error: `Số khách dự kiến vượt sức chứa người lớn tối đa của các phòng đã chọn (${totalCapacity} người lớn).`,
    }
  }

  return { allocations, error: "" }
}

function buildCheckoutSummaryLines(
  quote: Quote | null,
  roomTypeByCode: Map<string, RoomType>
) {
  if (!quote) return []

  const lines = new Map<string, CheckoutSummaryLine>()

  quote.calculations.forEach((calculation) => {
    const key = [
      calculation.roomTypeCode,
      calculation.paymentOption,
      calculation.cancellationPolicyCode,
    ].join("::")
    const existing = lines.get(key)
    const paymentLabel = calculation.paymentOption === "PAY_AT_HOTEL"
      ? "Thanh toán tại khách sạn"
      : "Thanh toán trực tuyến"

    if (existing) {
      existing.roomCount += 1
      existing.roomsTotal += Number(calculation.roomsTotal)
      existing.taxTotal += Number(calculation.taxTotal)
      existing.totalAmount += Number(calculation.totalAmount)
      return
    }

    const roomType = roomTypeByCode.get(calculation.roomTypeCode)

    lines.set(key, {
      key,
      roomTypeCode: calculation.roomTypeCode,
      roomTypeName: roomType?.name ?? calculation.roomTypeCode,
      cancellationPolicyName: calculation.cancellationPolicyName,
      paymentLabel,
      roomCount: 1,
      nights: calculation.nights,
      bedLabels: roomType ? getBedLabels(roomType) : ["Đang cập nhật"],
      maxAdults: roomType?.maxAdults ?? null,
      roomsTotal: Number(calculation.roomsTotal),
      taxTotal: Number(calculation.taxTotal),
      totalAmount: Number(calculation.totalAmount),
      currency: calculation.currency,
      dailyRates: calculation.dailyRates,
    })
  })

  return Array.from(lines.values())
}

export default function BookingPage() {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth()
  const [search, setSearch] = useState<SearchState>(initialSearch)
  const [contact, setContact] = useState<ContactState>({
    contactName: "",
    contactEmail: "",
    contactPhone: "",
    specialRequests: "",
  })
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [availableRoomsByType, setAvailableRoomsByType] = useState<Record<string, number>>({})
  const [selectedOptions, setSelectedOptions] = useState<SelectedBookingOption[]>([])
  const [roomDetails, setRoomDetails] = useState<Record<string, RoomCheckoutDetail>>({})
  const [expectedGuestCount, setExpectedGuestCount] = useState(1)
  const [quote, setQuote] = useState<Quote | null>(null)
  const [checkoutOpen, setCheckoutOpen] = useState(false)
  const [booking, setBooking] = useState<Booking | null>(null)
  const [loadingCatalog, setLoadingCatalog] = useState(false)
  const [searching, setSearching] = useState(false)
  const [calculating, setCalculating] = useState(false)
  const [creatingBooking, setCreatingBooking] = useState(false)
  const [error, setError] = useState("")

  const permissions = user?.permissions ?? []
  const accessMessage = isAuthenticated ? getAccessMessage(permissions) : null
  const nights = useMemo(() => getNights(search), [search])

  const roomTypeOptions = useMemo<RoomTypeOption[]>(() => {
    return roomTypes
      .filter((roomType) => roomType.isActive)
      .filter((roomType) => roomTypeMatchesGuestSearch(roomType, search))
      .map((roomType) => {
        return { roomType, availableCount: availableRoomsByType[roomType.code] ?? 0 }
      })
      .sort((left, right) => left.roomType.sortOrder - right.roomType.sortOrder)
  }, [availableRoomsByType, roomTypes, search])

  const galleryImages = useMemo<GalleryItem[]>(() => {
    return roomTypes
      .flatMap((roomType) =>
        roomType.images.map((image) => ({
          id: image.imageId,
          url: image.downloadUrl,
          alt: image.altText || roomType.name,
          sortOrder: image.sortOrder,
        }))
      )
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .slice(0, 6)
  }, [roomTypes])

  const selectedCount = selectedOptions.length
  const roomTypeByCode = useMemo(
    () => new Map(roomTypes.map((roomType) => [roomType.code, roomType])),
    [roomTypes]
  )
  const selectedEstimateTotal = useMemo(
    () => getSelectedEstimateTotal(selectedOptions, roomTypeByCode, nights),
    [nights, roomTypeByCode, selectedOptions]
  )
  const selectedCurrency = selectedOptions
    .map((selection) => roomTypeByCode.get(selection.roomTypeCode)?.currency)
    .find(Boolean) ?? "VND"
  const selectedPolicyLines = useMemo(() => {
    return selectedOptions.map((selection) => {
      const roomType = roomTypeByCode.get(selection.roomTypeCode)
      const option = roomType?.bookingOptions.find((candidate) => candidate.optionKey === selection.optionKey)
      const paymentLabel = selection.paymentOption === "PAY_AT_HOTEL" ? "Thanh toán tại khách sạn" : "Thanh toán trực tuyến"
      return `${roomType?.name ?? selection.roomTypeCode}: ${option?.cancellationPolicy.name ?? selection.cancellationPolicyCode} · ${paymentLabel}`
    })
  }, [roomTypeByCode, selectedOptions])
  const checkoutSummaryLines = useMemo(
    () => buildCheckoutSummaryLines(quote, roomTypeByCode),
    [quote, roomTypeByCode]
  )
  const selectedMaxAdults = useMemo(
    () => getSelectedMaxAdults(selectedOptions, roomTypeByCode),
    [roomTypeByCode, selectedOptions]
  )
  const cheapestBookingOffer = useMemo<CheapestBookingOffer | null>(() => {
    const offers = roomTypeOptions
      .filter((option) => option.availableCount > 0)
      .flatMap(({ roomType }) =>
        roomType.bookingOptions.map((bookingOption) => ({
          roomType,
          option: bookingOption,
          unitPrice: getBookingOptionUnitPrice(roomType, bookingOption),
        }))
      )
      .sort((left, right) => {
        const priceDiff = left.unitPrice - right.unitPrice
        if (priceDiff !== 0) return priceDiff
        return left.option.optionKey.localeCompare(right.option.optionKey)
      })

    return offers[0] ?? null
  }, [roomTypeOptions])

  const notifyError = useCallback((message: string) => {
    setError(message)
    toast.error(message)
  }, [])

  useEffect(() => {
    if (authLoading || !isAuthenticated || accessMessage) return

    let ignore = false
    const contactTimer = window.setTimeout(() => {
      if (ignore) return
      setContact((current) => ({
        ...current,
        contactName: current.contactName || user?.fullName || "",
        contactEmail: current.contactEmail || user?.email || "",
      }))
    }, 0)

    async function loadCatalog() {
      setLoadingCatalog(true)
      setError("")

      try {
        const [roomTypeData, availability] = await Promise.all([
          getBookingRoomTypes(),
          getAvailability(search.checkInDate, search.checkOutDate),
        ])
        if (ignore) return
        setRoomTypes(roomTypeData)
        setAvailableRoomsByType(mapAvailabilityByRoomTypeCode(availability, roomTypeData))
      } catch (loadError) {
        if (!ignore) {
          notifyError(getApiErrorMessage(loadError, "Không thể tải dữ liệu khách sạn"))
        }
      } finally {
        if (!ignore) setLoadingCatalog(false)
      }
    }

    loadCatalog()

    return () => {
      ignore = true
      window.clearTimeout(contactTimer)
    }
  }, [accessMessage, authLoading, isAuthenticated, notifyError, search.checkInDate, search.checkOutDate, user])

  function resetSelection(nextSearch: SearchState) {
    setSearch(nextSearch)
    setSelectedOptions([])
    setRoomDetails({})
    setExpectedGuestCount(1)
    setQuote(null)
    setCheckoutOpen(false)
  }

  async function runSearch() {
    setError("")

    if (nights < 1) {
      notifyError("Ngày trả phòng phải sau ngày nhận phòng.")
      return
    }

    setSearching(true)
    setSelectedOptions([])
    setRoomDetails({})
    setExpectedGuestCount(1)
    setQuote(null)
    setCheckoutOpen(false)

    try {
      const availability = await getAvailability(search.checkInDate, search.checkOutDate)
      setAvailableRoomsByType(mapAvailabilityByRoomTypeCode(availability, roomTypes))
      document.getElementById("choose-room")?.scrollIntoView({ behavior: "smooth", block: "start" })
    } catch (searchError) {
      setAvailableRoomsByType({})
      notifyError(getApiErrorMessage(searchError, "Không thể kiểm tra phòng còn trống"))
    } finally {
      setSearching(false)
    }
  }

  function addSelectedOption(roomType: RoomType, option: RoomTypeBookingOption) {
    if (getSelectionCount(selectedOptions, roomType.code) >= (availableRoomsByType[roomType.code] ?? 0)) {
      return
    }

    setQuote(null)
    setCheckoutOpen(false)
    const selectionId = crypto.randomUUID()
    setSelectedOptions((current) => {
      return [
        ...current,
        {
          selectionId,
          roomTypeCode: roomType.code,
          optionKey: option.optionKey,
          paymentOption: option.paymentOption,
          cancellationPolicyCode: option.cancellationPolicy.code,
        },
      ]
    })
    setExpectedGuestCount((current) => Math.max(current, selectedCount + 1))
    setRoomDetails((current) => ({
      ...current,
      [selectionId]: createDefaultRoomDetail(selectionId),
    }))
  }

  function removeSelectedOption(roomType: RoomType, option: RoomTypeBookingOption) {
    setQuote(null)
    setCheckoutOpen(false)
    setSelectedOptions((current) => {
      const removeIndex = current.findIndex(
        (selection) =>
          selection.roomTypeCode === roomType.code &&
          selection.optionKey === option.optionKey
      )
      if (removeIndex < 0) return current
      const removedSelection = current[removeIndex]
      setRoomDetails((details) => {
        const nextDetails = { ...details }
        delete nextDetails[removedSelection.selectionId]
        return nextDetails
      })
      return current.filter((_, index) => index !== removeIndex)
    })
  }

  function updateRoomDetail(
    selectionId: string,
    patch: Partial<Omit<RoomCheckoutDetail, "selectionId">>,
    shouldResetQuote = false
  ) {
    if (shouldResetQuote) {
      setQuote(null)
    }
    setRoomDetails((current) => {
      const existing = getRoomDetail(current, selectionId)
      return {
        ...current,
        [selectionId]: {
          ...existing,
          ...patch,
        },
      }
    })
  }

  function updateExpectedGuestCount(value: number) {
    setExpectedGuestCount(Math.max(1, value))
  }

  async function calculateSelectedRooms() {
    setError("")
    setCalculating(true)

    const plannedOptions = selectedOptions
    const normalizedGuestCount = Math.max(expectedGuestCount, plannedOptions.length)

    if (plannedOptions.length === 0) {
      notifyError("Vui lòng thêm ít nhất một phòng.")
      setCalculating(false)
      return
    }

    if (normalizedGuestCount !== expectedGuestCount) {
      setExpectedGuestCount(normalizedGuestCount)
    }

    const guestDistribution = distributeExpectedAdults(
      plannedOptions,
      normalizedGuestCount,
      roomTypeByCode
    )

    if (guestDistribution.error) {
      notifyError(guestDistribution.error)
      setCalculating(false)
      setCheckoutOpen(true)
      return
    }

    try {
      const calculations = await Promise.all(
        plannedOptions.map((selection, index) =>
          calculateBookingPrice({
            roomTypeCode: selection.roomTypeCode,
            paymentOption: selection.paymentOption,
            cancellationPolicyCode: selection.cancellationPolicyCode,
            checkInDate: search.checkInDate,
            checkOutDate: search.checkOutDate,
            adults: guestDistribution.allocations[index],
            children: 0,
          })
        )
      )
      setSelectedOptions(plannedOptions)
      setQuote({
        calculations,
        roomsTotal: calculations.reduce((sum, item) => sum + Number(item.roomsTotal), 0),
        taxTotal: calculations.reduce((sum, item) => sum + Number(item.taxTotal), 0),
        totalAmount: calculations.reduce((sum, item) => sum + Number(item.totalAmount), 0),
        currency: calculations[0]?.currency ?? "VND",
      })
      setCheckoutOpen(true)
    } catch (quoteError) {
      setQuote(null)
      setSelectedOptions([])
      notifyError(getApiErrorMessage(quoteError, "Không thể tính giá cho các phòng đã chọn"))
    } finally {
      setCalculating(false)
    }
  }

  async function submitBooking() {
    if (selectedOptions.length === 0 || !quote) {
      notifyError("Vui lòng bấm tiếp tục thanh toán để tính giá trước.")
      return
    }

    if (!contact.contactName.trim() || !contact.contactEmail.trim() || !contact.contactPhone.trim()) {
      notifyError("Vui lòng nhập đầy đủ tên, email và số điện thoại người liên hệ.")
      return
    }

    const missingGuestName = selectedOptions.find((selection) => {
      const detail = getRoomDetail(roomDetails, selection.selectionId)
      return !detail.guestFullName.trim()
    })

    if (missingGuestName) {
      notifyError("Vui lòng nhập họ tên người đại diện cho từng phòng.")
      return
    }

    const normalizedGuestCount = Math.max(expectedGuestCount, selectedOptions.length)
    const guestDistribution = distributeExpectedAdults(
      selectedOptions,
      normalizedGuestCount,
      roomTypeByCode
    )

    if (guestDistribution.error) {
      notifyError(guestDistribution.error)
      return
    }

    if (normalizedGuestCount !== expectedGuestCount) {
      setExpectedGuestCount(normalizedGuestCount)
    }

    const bookingRooms: BookingRoomItem[] = selectedOptions.map((selection, index) => ({
      roomTypeCode: selection.roomTypeCode,
      paymentOption: selection.paymentOption,
      cancellationPolicyCode: selection.cancellationPolicyCode,
      checkInDate: search.checkInDate,
      checkOutDate: search.checkOutDate,
      adults: guestDistribution.allocations[index],
      children: 0,
      guestFullName: getRoomDetail(roomDetails, selection.selectionId).guestFullName.trim(),
    }))

    setCreatingBooking(true)
    setError("")

    try {
      const created = await createBooking({
        contactName: contact.contactName.trim(),
        contactEmail: contact.contactEmail.trim(),
        contactPhone: contact.contactPhone.trim() || undefined,
        specialRequests: contact.specialRequests.trim() || undefined,
        rooms: bookingRooms,
      })
      setBooking(created)
      setCheckoutOpen(false)
      toast.success("Đã lưu booking thành công")
    } catch (bookingError) {
      notifyError(getApiErrorMessage(bookingError, "Không thể tạo booking"))
    } finally {
      setCreatingBooking(false)
    }
  }

  if (authLoading) {
    return (
      <div className="min-h-screen bg-background">
        <SiteHeader />
        <main className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-8">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-[420px] w-full" />
        </main>
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-background">
        <SiteHeader />
        <main className="mx-auto flex min-h-[70vh] max-w-2xl flex-col items-center justify-center gap-5 px-6 text-center">
          <Badge variant="outline">Cần đăng nhập</Badge>
          <h1 className="font-serif text-4xl tracking-tight">Đăng nhập để chọn phòng.</h1>
          <p className="text-muted-foreground">
            Tài khoản khách hàng cần có `booking:create` và `room:read` để xem phòng, tính giá và giữ phòng.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <Button asChild size="lg">
              <Link href="/login">Đăng nhập</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/register">Tạo tài khoản</Link>
            </Button>
          </div>
        </main>
      </div>
    )
  }

  if (accessMessage) {
    return (
      <div className="min-h-screen bg-background">
        <SiteHeader />
        <main className="mx-auto flex min-h-[70vh] max-w-2xl flex-col items-center justify-center gap-5 px-6 text-center">
          <Alert variant="destructive">
            <AlertTitle>Tài khoản chưa đủ quyền đặt phòng</AlertTitle>
            <AlertDescription>{accessMessage}</AlertDescription>
          </Alert>
        </main>
      </div>
    )
  }

  if (booking) {
    return (
      <div className="min-h-screen bg-background">
        <SiteHeader />
        <main className="mx-auto flex min-h-[70vh] max-w-2xl flex-col items-center justify-center gap-5 px-6 text-center">
          <div className="flex size-16 items-center justify-center rounded-full bg-[var(--success)] text-white">
            <Check />
          </div>
          <Badge variant="success">Thanh toán thành công</Badge>
          <h1 className="font-serif text-4xl tracking-tight">Đặt phòng đã được ghi nhận.</h1>
          <p className="text-muted-foreground">
            Mã đặt phòng <span className="font-semibold text-foreground">{booking.bookingCode}</span>.
            Tạm thời FE đang xác nhận thanh toán thành công sau khi tạo booking. Ở backend, phòng được giữ đến{" "}
            <span className="font-semibold text-foreground">
              {booking.holdExpiresAt
                ? new Date(booking.holdExpiresAt).toLocaleString("vi-VN")
                : "thời điểm được hệ thống xác nhận"}
            </span>.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <Button asChild>
              <Link href="/profile/bookings">Xem đơn đặt phòng</Link>
            </Button>
            <Button asChild variant="outline">
              <Link href="/booking">Đặt thêm phòng</Link>
            </Button>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background pb-28 font-sans">
      <SiteHeader />

      <main className="mx-auto flex max-w-7xl flex-col gap-8 px-6 py-8 lg:px-8">
        {error && (
          <Alert variant="destructive">
            <AlertTitle>Không thể tiếp tục</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <HotelHeader />
        <HotelGallery images={galleryImages} loading={loadingCatalog} />
        <HotelDetails roomTypes={roomTypes} cheapestOffer={cheapestBookingOffer} search={search} />

        <section id="choose-room" className="scroll-mt-28">
          <div className="flex flex-col gap-2">
            <h2 className="text-2xl font-bold tracking-tight">Chọn phòng</h2>
          </div>
          <HotelSearchBar
            search={search}
            loading={searching || loadingCatalog}
            minNightPrice={cheapestBookingOffer?.unitPrice ?? 0}
            onChange={resetSelection}
            onSearch={runSearch}
          />
          <p className="mb-4 text-muted-foreground">
            Bộ lọc khách dùng để tìm room type có sức chứa tối thiểu phù hợp. Số khách dự kiến cho toàn đơn sẽ nhập ở bước thanh toán.
          </p>

          <RoomTypeList
            options={roomTypeOptions}
            loading={loadingCatalog || searching}
            selectedOptions={selectedOptions}
            cheapestOptionKey={cheapestBookingOffer?.option.optionKey}
            nights={nights}
            onAddOption={addSelectedOption}
            onRemoveOption={removeSelectedOption}
          />
        </section>
      </main>

      {selectedCount > 0 && (
        <BottomCheckoutBar
          selectedCount={selectedCount}
          nights={nights}
          adults={search.adults}
          childrenCount={search.children}
          estimatedTotal={selectedEstimateTotal}
          currency={selectedCurrency}
          calculating={calculating}
          onContinue={calculateSelectedRooms}
        />
      )}

      <CheckoutDialog
        open={checkoutOpen}
        onOpenChange={setCheckoutOpen}
        search={search}
        contact={contact}
        quote={quote}
        policyLines={selectedPolicyLines}
        summaryLines={checkoutSummaryLines}
        selectedOptions={selectedOptions}
        roomDetails={roomDetails}
        expectedGuestCount={expectedGuestCount}
        selectedMaxAdults={selectedMaxAdults}
        roomTypeByCode={roomTypeByCode}
        creating={creatingBooking}
        onContactChange={setContact}
        onRoomDetailChange={updateRoomDetail}
        onExpectedGuestCountChange={updateExpectedGuestCount}
        onRefreshQuote={calculateSelectedRooms}
        onSubmit={submitBooking}
      />
    </div>
  )
}

function HotelHeader() {
  return (
    <section className="flex flex-col justify-between gap-4 lg:flex-row lg:items-start">
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-bold tracking-tight md:text-4xl">TripStay Hotel</h1>
          <div className="flex gap-0.5 text-yellow-500">
            {Array.from({ length: 5 }).map((_, index) => (
              <Star key={index} className="size-4 fill-current" />
            ))}
          </div>
        </div>
        <p className="flex items-center gap-2 text-muted-foreground">
          <MapPin />
          33 Đường Bạch Đằng, Phường 2, Quận Tân Bình, TP. Hồ Chí Minh
        </p>
      </div>
      <div className="flex gap-3">
        <Button variant="outline">
          <Heart data-icon="inline-start" />
          Lưu
        </Button>
        <Button variant="outline">
          <Share2 data-icon="inline-start" />
          Chia sẻ
        </Button>
      </div>
    </section>
  )
}

function HotelGallery({
  images,
  loading,
}: {
  images: GalleryItem[]
  loading: boolean
}) {
  if (loading) {
    return <Skeleton className="h-[420px] w-full rounded-2xl" />
  }

  const slots = images.length > 0 ? images : Array.from({ length: 5 }).map((_, index) => ({
    id: `fallback-${index}`,
    url: "",
    alt: "TripStay Hotel",
    sortOrder: index,
  }))

  return (
    <section className="grid h-[420px] gap-2 overflow-hidden rounded-2xl md:grid-cols-[1.35fr_1fr_1fr]">
      <GalleryTile item={slots[0]} className="md:row-span-2" />
      <GalleryTile item={slots[1]} />
      <GalleryTile item={slots[2]} />
      <GalleryTile item={slots[3]} />
      <GalleryTile item={slots[4]} overlay={`Xem tất cả ảnh (${images.length || 5})`} />
    </section>
  )
}

function GalleryTile({
  item,
  className,
  overlay,
}: {
  item?: { url: string; alt: string }
  className?: string
  overlay?: string
}) {
  return (
    <div
      className={cn(
        "relative flex min-h-0 items-end overflow-hidden bg-muted p-4",
        className
      )}
      style={item?.url ? { backgroundImage: `url(${item.url})`, backgroundSize: "cover", backgroundPosition: "center" } : undefined}
      aria-label={item?.alt}
    >
      {!item?.url && (
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_var(--primary),_transparent_45%),linear-gradient(135deg,_var(--muted),_var(--background))] opacity-30" />
      )}
      {overlay && (
        <Badge className="relative ml-auto bg-background text-foreground">
          <ImageIcon data-icon="inline-start" />
          {overlay}
        </Badge>
      )}
    </div>
  )
}

function HotelDetails({
  roomTypes,
  cheapestOffer,
  search,
}: {
  roomTypes: RoomType[]
  cheapestOffer: CheapestBookingOffer | null
  search: SearchState
}) {
  const amenities = Array.from(
    new Map(roomTypes.flatMap((roomType) => roomType.amenities).map((amenity) => [amenity.code, amenity])).values()
  ).slice(0, 8)

  return (
    <section className="grid gap-8 lg:grid-cols-[1fr_360px]">
      <div className="flex flex-col gap-8">
        <div className="grid grid-cols-2 gap-5 md:grid-cols-5">
          <FeatureIcon icon={BedDouble} label={roomTypes[0]?.name ?? "Suite"} />
          <FeatureIcon icon={Star} label="Vị trí lý tưởng" />
          <FeatureIcon icon={Users} label="Phòng gia đình" />
          <FeatureIcon icon={Wifi} label="Wi‑Fi miễn phí trong phòng" />
          <FeatureIcon icon={Info} label="+2 mục khác" />
        </div>

        <Separator />

        <div className="grid gap-8 md:grid-cols-[360px_1fr]">
          <div className="flex items-center gap-5">
            <div className="text-5xl font-bold text-primary">8,4</div>
            <div>
              <div className="text-xl font-semibold text-primary">Rất tốt</div>
              <div className="underline underline-offset-4">Tất cả 1.448 đánh giá</div>
            </div>
          </div>
          <p className="text-base font-medium leading-relaxed">
            Mỗi lần về VN là tôi sẽ ở khách sạn này. Vừa tiện vì gần sân bay, xung quanh đều có các quán ăn.
          </p>
        </div>

        <Separator />

        <div className="flex flex-col gap-5">
          <h2 className="text-xl font-bold">Xem xung quanh đây</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <NearbyItem icon={Plane} label="Khu thương mại: Khu vực sân bay quốc tế" distance="1,5km" />
            <NearbyItem icon={Star} label="Điểm tham quan: Công viên Gia Định" distance="640m" />
            <NearbyItem icon={BellRing} label="Vui chơi giải trí: Trung Tâm Hội Nghị" distance="950m" />
            <NearbyItem icon={Train} label="Nhà ga: Ga Gò Vấp" distance="1,8km" />
          </div>
          <button className="w-fit text-base font-semibold underline underline-offset-4">Xem trên bản đồ</button>
        </div>

        <div className="flex flex-col gap-5">
          <h2 className="text-xl font-bold">Tiện nghi</h2>
          <div className="grid gap-4 md:grid-cols-2">
            {amenities.length > 0 ? amenities.map((amenity) => (
              <AmenityItem key={amenity.code} label={amenity.name} />
            )) : (
              <>
                <AmenityItem label="Đón tại sân bay" />
                <AmenityItem label="Gọi điện đánh thức" />
                <AmenityItem label="Nhân viên hành lý" />
                <AmenityItem label="Thang máy" />
              </>
            )}
          </div>
          <button className="w-fit text-base font-semibold underline underline-offset-4">Tất cả tiện nghi</button>
        </div>
      </div>

      <BestPriceCard
        offer={cheapestOffer}
        search={search}
      />
    </section>
  )
}

function FeatureIcon({
  icon: Icon,
  label,
}: {
  icon: ComponentType<{ className?: string }>
  label: string
}) {
  return (
    <div className="flex flex-col items-center gap-2 text-center">
      <Icon className="text-primary" />
      <span className="font-medium">{label}</span>
    </div>
  )
}

function NearbyItem({
  icon: Icon,
  label,
  distance,
}: {
  icon: ComponentType<{ className?: string }>
  label: string
  distance: string
}) {
  return (
    <div className="flex items-center gap-3 text-base">
      <Icon className="text-muted-foreground" />
      <span className="truncate">{label}</span>
      <span className="text-muted-foreground">({distance})</span>
    </div>
  )
}

function AmenityItem({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 text-base">
      <CheckCircle2 className="text-muted-foreground" />
      <span>{label}</span>
    </div>
  )
}

function BestPriceCard({
  offer,
  search,
}: {
  offer: CheapestBookingOffer | null
  search: SearchState
}) {
  const roomType = offer?.roomType ?? null
  const bookingOption = offer?.option ?? null
  const unitPrice = offer?.unitPrice ?? 0
  const estimate = unitPrice * search.rooms * getNights(search)

  return (
    <Card className="h-fit overflow-hidden shadow-xl lg:sticky lg:top-36">
      <div className="bg-cyan-50 px-6 py-4 text-xl font-bold text-cyan-800">
        Giá tốt nhất của hôm nay!
      </div>
      <CardContent className="flex flex-col gap-4 p-6">
        <div>
          <div className="text-3xl font-bold text-primary">{money(unitPrice, roomType?.currency ?? "VND")}</div>
          <div className="text-sm text-muted-foreground">
            Tổng giá: <span className="font-semibold text-foreground">{money(estimate, roomType?.currency ?? "VND")}</span>
          </div>
          <div className="text-sm text-muted-foreground">
            {search.rooms} phòng × {getNights(search)} đêm bao gồm thuế & phí sau khi tính giá
          </div>
        </div>
        <div className="font-semibold">{roomType?.name ?? "Chọn phòng"}</div>
        <InfoPill icon={BedDouble} label={roomType ? getBedSummary(roomType) : "Chưa có loại phòng"} />
        <InfoPill icon={Ban} label={bookingOption?.cancellationPolicy.name ?? "Chính sách theo loại phòng"} />
        <InfoPill icon={Check} label="Xác nhận ngay" />
        <InfoPill icon={CreditCard} label="Thanh toán sau bước chọn phòng" />
        <Button size="lg" onClick={() => document.getElementById("choose-room")?.scrollIntoView({ behavior: "smooth" })}>
          Thêm lựa chọn
        </Button>
        <div className="text-center text-sm text-muted-foreground">Chúng tôi khớp giá</div>
      </CardContent>
    </Card>
  )
}

function InfoPill({
  icon: Icon,
  label,
}: {
  icon: ComponentType<{ className?: string }>
  label: string
}) {
  return (
    <div className="flex items-center gap-3 text-sm">
      <Icon className="text-muted-foreground" />
      <span>{label}</span>
    </div>
  )
}

function HotelSearchBar({
  search,
  loading,
  minNightPrice,
  onChange,
  onSearch,
}: {
  search: SearchState
  loading: boolean
  minNightPrice: number
  onChange: (search: SearchState) => void
  onSearch: () => void
}) {
  return (
    <div className="sticky top-16 z-40 mb-5 bg-background/95 py-3 backdrop-blur">
      <div className="grid w-full grid-cols-1 gap-2 rounded-xl border-2 border-primary bg-background p-1 shadow-sm lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-center">
        <DateRangePicker search={search} minNightPrice={minNightPrice} onChange={onChange} />
        <GuestPicker search={search} onChange={onChange} />
        <Button className="h-12 rounded-lg px-6 text-base" onClick={onSearch} disabled={loading}>
          {loading ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Search data-icon="inline-start" />}
          Tìm
        </Button>
      </div>
    </div>
  )
}

function DateRangePicker({
  search,
  minNightPrice,
  onChange,
}: {
  search: SearchState
  minNightPrice: number
  onChange: (search: SearchState) => void
}) {
  const [cursorMonth, setCursorMonth] = useState(startOfMonth(parseISO(search.checkInDate)))
  const nights = getNights(search)

  function selectDate(date: Date) {
    const iso = format(date, "yyyy-MM-dd")
    if (iso <= search.checkInDate || search.checkOutDate !== search.checkInDate) {
      onChange({ ...search, checkInDate: iso, checkOutDate: iso })
      return
    }
    onChange({ ...search, checkOutDate: format(date, "yyyy-MM-dd") })
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button className="flex h-12 w-full items-center gap-4 rounded-lg px-4 hover:bg-muted">
          <CalendarDays />
          <span className="text-base font-semibold">{headerDate(search.checkInDate)}</span>
          <span className="text-base font-semibold">-</span>
          <span className="text-base font-semibold">{headerDate(search.checkOutDate)}</span>
          <Badge variant="secondary">{nights} đêm</Badge>
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[min(860px,calc(100vw-2rem))] p-4">
        <div className="grid gap-4 md:grid-cols-2">
          {[cursorMonth, addMonths(cursorMonth, 1)].map((month, index) => (
            <Calendar
              key={month.toISOString()}
              month={month}
              onMonthChange={(nextMonth) => setCursorMonth(index === 0 ? nextMonth : addMonths(nextMonth, -1))}
              selectedRange={{
                from: parseISO(search.checkInDate),
                to: search.checkOutDate === search.checkInDate ? undefined : parseISO(search.checkOutDate),
              }}
              minDate={today}
              onSelect={selectDate}
              cellClassName="h-14 w-14 flex-col gap-0.5"
              renderDayContent={(day, state) => (
                <>
                  <span className="text-base font-bold">{format(day, "d")}</span>
                  {!state.disabled && (
                    <span className={cn("text-[11px]", state.selected ? "text-primary-foreground/85" : "text-muted-foreground")}>
                      {compactMoney(minNightPrice)}
                    </span>
                  )}
                </>
              )}
            />
          ))}
        </div>
        <div className="mt-6 flex items-center justify-between text-sm text-muted-foreground">
          <span>Giá trong từng ô là giá đêm thấp nhất hiện có</span>
          <span>{headerDate(search.checkInDate)} - {headerDate(search.checkOutDate)} ({nights} đêm)</span>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function GuestPicker({
  search,
  onChange,
}: {
  search: SearchState
  onChange: (search: SearchState) => void
}) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button className="flex h-12 w-full items-center gap-3 rounded-lg px-4 hover:bg-muted">
          <Users />
          <span className="text-base font-semibold">
            Sức chứa từ {search.adults + search.children} khách
          </span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[360px] p-6">
        <div className="flex flex-col gap-6">
          <CounterRow
            label="Người lớn tối thiểu"
            description="18+ tuổi"
            value={search.adults}
            min={1}
            onChange={(adults) => onChange({ ...search, adults })}
          />
          <CounterRow
            label="Trẻ em tối thiểu"
            description="0-17 tuổi"
            value={search.children}
            min={0}
            onChange={(children) => onChange({ ...search, children })}
          />
          <Button className="ml-auto px-8">Xong</Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function CounterRow({
  label,
  description,
  value,
  min,
  max,
  onChange,
}: {
  label: string
  description?: string
  value: number
  min: number
  max?: number
  onChange: (value: number) => void
}) {
  const canIncrease = max === undefined || value < max

  return (
    <div className="flex items-center justify-between gap-4">
      <div>
        <div className="text-base">{label}</div>
        {description && <div className="text-sm text-muted-foreground">{description}</div>}
      </div>
      <div className="flex items-center gap-4">
        <Button type="button" variant="outline" size="icon" disabled={value <= min} onClick={() => onChange(Math.max(min, value - 1))}>
          <Minus />
        </Button>
        <span className="w-6 text-center text-base font-medium">{value}</span>
        <Button
          type="button"
          variant="outline"
          size="icon"
          disabled={!canIncrease}
          onClick={() => onChange(max === undefined ? value + 1 : Math.min(max, value + 1))}
        >
          <Plus />
        </Button>
      </div>
    </div>
  )
}

function RoomTypeList({
  options,
  loading,
  selectedOptions,
  cheapestOptionKey,
  nights,
  onAddOption,
  onRemoveOption,
}: {
  options: RoomTypeOption[]
  loading: boolean
  selectedOptions: SelectedBookingOption[]
  cheapestOptionKey?: string
  nights: number
  onAddOption: (roomType: RoomType, option: RoomTypeBookingOption) => void
  onRemoveOption: (roomType: RoomType, option: RoomTypeBookingOption) => void
}) {
  if (loading) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-80 w-full rounded-2xl" />
        <Skeleton className="h-80 w-full rounded-2xl" />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      {options.map((option) => {
        const roomType = option.roomType
        const availableCount = option.availableCount
        const selectedInRoomType = getSelectionCount(selectedOptions, roomType.code)
        const primaryImage = roomType.images.find((image) => image.isPrimary) ?? roomType.images[0]
        const canAddMore = selectedInRoomType < availableCount
        const sortedBookingOptions = sortBookingOptionsByPrice(roomType)

        return (
          <Card key={roomType.code} className={cn("overflow-hidden", selectedInRoomType > 0 && "border-green-500")}>
            <CardContent className="grid items-stretch gap-0 p-0 lg:grid-cols-[430px_minmax(0,1fr)]">
              <div className="border-r p-5">
                <GalleryTile
                  item={primaryImage ? { url: primaryImage.downloadUrl, alt: primaryImage.altText } : undefined}
                  className="mb-5 h-56 rounded-xl"
                  overlay={`${roomType.images.length || 1}`}
                />
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 flex-col gap-2">
                    <h3 className="text-xl font-bold">{roomType.name}</h3>
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant={availableCount > 0 ? "success" : "destructive"} className="text-sm">
                        Còn {availableCount} phòng
                      </Badge>
                      {selectedInRoomType > 0 && (
                        <Badge variant="secondary" className="text-sm">
                          Đã chọn {selectedInRoomType}/{availableCount}
                        </Badge>
                      )}
                    </div>
                  </div>
                  <Info className="text-muted-foreground" />
                </div>
                <div className="mt-4 flex items-center gap-2 text-base font-semibold">
                  <BedDouble />
                  {getBedSummary(roomType)}
                </div>
                <div className="mt-4 grid gap-3 text-sm md:grid-cols-2">
                  <RoomSpec label={`${roomType.sizeSqm ?? "-"} m²`} />
                  <RoomSpec label={`Tối đa ${roomType.maxOccupancy} khách`} />
                  <RoomSpec label="Wi‑Fi miễn phí" />
                  <RoomSpec label="Phòng tắm riêng" />
                </div>
                <button className="mt-6 text-base font-semibold underline underline-offset-4">Thông tin phòng</button>
              </div>

              <div className="relative min-w-0 overflow-hidden">
                {availableCount > 0 && sortedBookingOptions.length > 0 ? (
                  <div className="flex h-full min-w-0 overflow-x-auto scroll-smooth">
                    {sortedBookingOptions.map((bookingOption) => (
                      <RoomChoiceCard
                        key={bookingOption.optionKey}
                        roomType={roomType}
                        option={bookingOption}
                        selectedCount={getSelectionCount(selectedOptions, roomType.code, bookingOption.optionKey)}
                        isCheapestToday={bookingOption.optionKey === cheapestOptionKey}
                        canAddMore={canAddMore}
                        nights={nights}
                        onAdd={() => onAddOption(roomType, bookingOption)}
                        onRemove={() => onRemoveOption(roomType, bookingOption)}
                      />
                    ))}
                  </div>
                ) : (
                  <div className="flex h-full min-h-[260px] items-center justify-center p-6 text-center text-muted-foreground">
                    {availableCount === 0
                      ? "Không còn phòng trống trong khoảng ngày này."
                      : "Loại phòng này chưa có option bán đang hoạt động."}
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}

function RoomSpec({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-2">
      <CheckCircle2 className="text-muted-foreground" />
      <span>{label}</span>
    </div>
  )
}

function RoomChoiceCard({
  roomType,
  option,
  selectedCount,
  isCheapestToday,
  canAddMore,
  nights,
  onAdd,
  onRemove,
}: {
  roomType: RoomType
  option: RoomTypeBookingOption
  selectedCount: number
  isCheapestToday: boolean
  canAddMore: boolean
  nights: number
  onAdd: () => void
  onRemove: () => void
}) {
  const unitPrice = getBookingOptionUnitPrice(roomType, option)
  const basePrice = Number(roomType.basePrice)
  const priceDifference = unitPrice - basePrice
  const displayRoomCount = Math.max(1, selectedCount)
  const totalPrice = unitPrice * displayRoomCount * Math.max(1, nights)
  const paymentLabel = option.paymentOption === "PAY_AT_HOTEL"
    ? "Thanh toán tại khách sạn"
    : "Thanh toán trước trực tuyến"

  return (
    <div className={cn(
      "flex min-h-[430px] w-[360px] shrink-0 flex-col justify-between border-r p-5",
      selectedCount > 0 && "bg-green-50/70"
    )}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          {isCheapestToday ? (
            <Badge className="rounded-md border-0 bg-cyan-50 px-3 py-1 text-sm font-semibold text-cyan-800">
              Giá thấp nhất hôm nay
            </Badge>
          ) : (
            <div className="text-base font-bold text-foreground">Chỉ tiền phòng</div>
          )}
          <Info className="text-muted-foreground" />
        </div>
        <InfoPill icon={Ban} label={option.cancellationPolicy.name} />
        <InfoPill icon={CreditCard} label={paymentLabel} />
        <InfoPill icon={Users} label={`Tối đa ${roomType.maxAdults} người lớn`} />
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          {priceDifference < 0 && (
            <>
              <Badge className="rounded-md border-0 bg-rose-600 px-2 py-1 text-sm font-bold text-white">
                {money(priceDifference, roomType.currency)}
              </Badge>
              <Badge className="rounded-md border-0 bg-rose-50 px-2 py-1 text-sm font-medium text-rose-600">
                Giảm Giá Đặc Biệt
              </Badge>
            </>
          )}
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="text-3xl font-semibold leading-none text-[var(--accent)]">
            {money(unitPrice, roomType.currency)}
          </div>
          {priceDifference < 0 && (
            <div className="relative text-lg leading-none text-muted-foreground">
              <span>{money(basePrice, roomType.currency)}</span>
              <span className="absolute left-0 top-1/2 h-0.5 w-full -translate-y-1/2 rotate-[-8deg] bg-rose-600" />
            </div>
          )}
        </div>
        <div className="flex flex-col gap-1 text-sm text-muted-foreground">
          <div>
            Tổng giá:{" "}
            <span className="font-semibold text-foreground">
              {money(totalPrice, roomType.currency)}
            </span>
          </div>
          <div>
            {displayRoomCount} phòng x {Math.max(1, nights)} đêm bao gồm thuế & phí
          </div>
        </div>
        {selectedCount > 0 ? (
          <div className="grid h-11 grid-cols-[44px_1fr_44px] overflow-hidden rounded-md border border-green-200 bg-green-50">
            <Button
              type="button"
              variant="ghost"
              className="h-11 rounded-none text-green-800 hover:bg-green-100"
              onClick={onRemove}
              aria-label={`Giảm ${option.cancellationPolicy.name}`}
            >
              <Minus />
            </Button>
            <div className="flex items-center justify-center text-sm font-semibold text-green-900">
              {selectedCount} phòng
            </div>
            <Button
              type="button"
              variant="ghost"
              className="h-11 rounded-none text-green-800 hover:bg-green-100"
              onClick={onAdd}
              disabled={!canAddMore}
              aria-label={`Thêm ${option.cancellationPolicy.name}`}
            >
              <Plus />
            </Button>
          </div>
        ) : (
          <Button variant="default" onClick={onAdd} disabled={!canAddMore}>
            <Plus data-icon="inline-start" />
            Thêm
          </Button>
        )}
      </div>
    </div>
  )
}

function BottomCheckoutBar({
  selectedCount,
  nights,
  adults,
  childrenCount,
  estimatedTotal,
  currency,
  calculating,
  onContinue,
}: {
  selectedCount: number
  nights: number
  adults: number
  childrenCount: number
  estimatedTotal: number
  currency: string
  calculating: boolean
  onContinue: () => void
}) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-50 border-t bg-background/95 px-4 py-3 shadow-[0_-12px_28px_rgba(0,0,0,0.08)] backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="success" className="rounded-md">
              {selectedCount} phòng đã chọn
            </Badge>
            <span className="text-sm text-muted-foreground">
              {nights} đêm · lọc sức chứa từ {adults + childrenCount} khách
            </span>
          </div>
          <div className="text-sm text-muted-foreground">
            Tạm tính trước VAT/phí xác nhận:{" "}
            <span className="text-lg font-semibold text-foreground">
              {money(estimatedTotal, currency)}
            </span>
          </div>
        </div>

        <Button
          type="button"
          onClick={onContinue}
          disabled={calculating}
          variant="success"
          size="lg"
          className="h-12 px-6 text-base font-semibold md:min-w-[240px]"
          aria-label={`Tiếp tục thanh toán ${selectedCount} phòng đã chọn`}
        >
          {calculating ? (
            <Loader2 data-icon="inline-start" className="animate-spin" />
          ) : (
            <CreditCard data-icon="inline-start" />
          )}
          Tiếp tục thanh toán
        </Button>
      </div>
    </div>
  )
}

function CheckoutDialog({
  open,
  onOpenChange,
  search,
  contact,
  quote,
  policyLines,
  summaryLines,
  selectedOptions,
  roomDetails,
  expectedGuestCount,
  selectedMaxAdults,
  roomTypeByCode,
  creating,
  onContactChange,
  onRoomDetailChange,
  onExpectedGuestCountChange,
  onRefreshQuote,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  search: SearchState
  contact: ContactState
  quote: Quote | null
  policyLines: string[]
  summaryLines: CheckoutSummaryLine[]
  selectedOptions: SelectedBookingOption[]
  roomDetails: Record<string, RoomCheckoutDetail>
  expectedGuestCount: number
  selectedMaxAdults: number
  roomTypeByCode: Map<string, RoomType>
  creating: boolean
  onContactChange: (contact: ContactState) => void
  onRoomDetailChange: (
    selectionId: string,
    patch: Partial<Omit<RoomCheckoutDetail, "selectionId">>,
    shouldResetQuote?: boolean
  ) => void
  onExpectedGuestCountChange: (guestCount: number) => void
  onRefreshQuote: () => void
  onSubmit: () => void
}) {
  const minimumExpectedGuests = Math.max(1, selectedOptions.length)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] max-w-7xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Xác nhận thông tin và thanh toán</DialogTitle>
          <DialogDescription>
            Người liên hệ có thể khác người lưu trú. Mỗi phòng chỉ cần họ tên đại diện; giấy tờ tùy thân sẽ bổ sung khi check-in.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
          <div className="flex flex-col gap-5">
            <section className="flex flex-col gap-4 rounded-lg border bg-card p-5">
              <div className="flex items-center gap-3">
                <UserRound className="text-muted-foreground" />
                <div>
                  <h3 className="text-lg font-semibold">Thông tin người liên hệ</h3>
                  <p className="text-sm text-muted-foreground">
                    Đây là người nhận xác nhận đặt phòng, không bắt buộc là người trực tiếp lưu trú.
                  </p>
                </div>
              </div>
              <ContactForm contact={contact} onChange={onContactChange} />
            </section>

            <section className="flex flex-col gap-4 rounded-lg border bg-card p-5">
              <div className="flex items-center gap-3">
                <Users className="text-muted-foreground" />
                <div>
                  <h3 className="text-lg font-semibold">Khách dự kiến</h3>
                  <p className="text-sm text-muted-foreground">
                    Áp dụng cho toàn bộ đơn. Bộ lọc ở trang chọn phòng chỉ dùng để tìm room type phù hợp.
                  </p>
                </div>
              </div>
              <CounterRow
                label="Khách dự kiến"
                description={`Tối đa ${selectedMaxAdults || "-"} người lớn theo các phòng đã chọn`}
                value={Math.max(expectedGuestCount, minimumExpectedGuests)}
                min={minimumExpectedGuests}
                max={selectedMaxAdults || undefined}
                onChange={onExpectedGuestCountChange}
              />
            </section>

            <section className="flex flex-col gap-4 rounded-lg border bg-card p-5">
              <div className="flex items-center gap-3">
                <BedDouble className="text-muted-foreground" />
                <div>
                  <h3 className="text-lg font-semibold">Thông tin từng phòng</h3>
                  <p className="text-sm text-muted-foreground">
                    Nhập họ tên đại diện cho từng phòng. Đây chưa phải bước khai báo căn cước.
                  </p>
                </div>
              </div>
              <div className="grid gap-3 lg:grid-cols-2">
                {selectedOptions.map((selection, index) => {
                  const roomType = roomTypeByCode.get(selection.roomTypeCode)
                  const detail = getRoomDetail(roomDetails, selection.selectionId)
                  return (
                    <RoomCheckoutDetailCard
                      key={selection.selectionId}
                      index={index}
                      selection={selection}
                      roomType={roomType}
                      detail={detail}
                      onChange={onRoomDetailChange}
                    />
                  )
                })}
              </div>
            </section>

            <section className="flex flex-col gap-4 rounded-lg border bg-card p-5">
              <div className="flex items-center gap-3">
                <ReceiptText className="text-muted-foreground" />
                <div>
                  <h3 className="text-lg font-semibold">Chi tiết đặt phòng</h3>
                  <p className="text-sm text-muted-foreground">
                    Mỗi dòng tương ứng một loại phòng, chính sách hủy và hình thức thanh toán đã chọn.
                  </p>
                </div>
              </div>
              <div className="flex flex-col gap-3">
                {quote ? (
                  summaryLines.map((line) => (
                    <CheckoutSummaryLineItem key={line.key} line={line} />
                  ))
                ) : (
                  <div className="rounded-md bg-muted p-4 text-sm text-muted-foreground">
                    Bấm cập nhật giá để tính lại VAT/phí trước khi xác nhận.
                  </div>
                )}
              </div>
            </section>
          </div>

          <div className="flex h-fit flex-col gap-4 rounded-lg border bg-card p-5 xl:sticky xl:top-4">
            <h3 className="text-lg font-semibold">Tổng thanh toán</h3>
            <InfoLine icon={CalendarDays} label="Ngày" value={`${displayDate(search.checkInDate)} - ${displayDate(search.checkOutDate)} (${getNights(search)} đêm)`} />
            <InfoLine icon={Users} label="Bộ lọc sức chứa" value={`Từ ${search.adults + search.children} khách/phòng`} />
            <InfoLine icon={UserRound} label="Khách dự kiến" value={`${Math.max(expectedGuestCount, minimumExpectedGuests)} khách toàn đơn`} />
            {policyLines.length > 0 && (
              <InfoLine icon={ShieldCheck} label="Điều kiện đã chọn" value={`${policyLines.length} lựa chọn phòng/chính sách`} />
            )}
            <Separator />
            {quote && (
              <>
                <PriceLine label="Tiền phòng" value={money(quote.roomsTotal, quote.currency)} />
                <PriceLine label="VAT & phí" value={money(quote.taxTotal, quote.currency)} />
                <div className="flex justify-between gap-3 text-lg font-semibold">
                  <span>Tổng cộng</span>
                  <span className="text-[var(--accent)]">{money(quote.totalAmount, quote.currency)}</span>
                </div>
                <div className="rounded-md bg-green-50 p-3 text-sm text-green-800">
                  Tạm thời chưa có màn thanh toán SePay ở frontend. Khi bấm thanh toán, hệ thống tạo booking và coi giao dịch là thành công ở bước giao diện.
                </div>
              </>
            )}
            {!quote && (
              <div className="rounded-md bg-yellow-50 p-3 text-sm text-yellow-800">
                Cần cập nhật giá trước khi xác nhận thanh toán.
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Quay lại chọn phòng</Button>
          {quote ? (
            <Button variant="success" onClick={onSubmit} disabled={creating}>
              {creating && <Loader2 data-icon="inline-start" className="animate-spin" />}
              Thanh toán và xác nhận
            </Button>
          ) : (
            <Button onClick={onRefreshQuote}>
              Cập nhật giá
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function RoomCheckoutDetailCard({
  index,
  selection,
  roomType,
  detail,
  onChange,
}: {
  index: number
  selection: SelectedBookingOption
  roomType?: RoomType
  detail: RoomCheckoutDetail
  onChange: (
    selectionId: string,
    patch: Partial<Omit<RoomCheckoutDetail, "selectionId">>,
    shouldResetQuote?: boolean
  ) => void
}) {
  const bedLabels = roomType ? getBedLabels(roomType) : ["Đang cập nhật"]

  return (
    <div className="flex flex-col gap-4 rounded-lg border bg-background p-4">
      <div className="flex flex-col gap-1">
        <div className="font-semibold">Phòng {index + 1}: {roomType?.name ?? selection.roomTypeCode}</div>
      </div>

      <LabeledInput label="Họ tên người đại diện phòng">
        <Input
          value={detail.guestFullName}
          placeholder="Nguyễn Văn A"
          onChange={(event) => onChange(selection.selectionId, { guestFullName: event.target.value })}
        />
      </LabeledInput>

      <div className="grid gap-3 rounded-md bg-muted p-3 text-sm md:grid-cols-2">
        <div className="flex items-start gap-2">
          <BedDouble className="text-muted-foreground" />
          <div className="flex flex-col gap-1">
            {bedLabels.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Users className="text-muted-foreground" />
          <span>Tối đa {roomType?.maxAdults ?? "-"} người lớn</span>
        </div>
      </div>
    </div>
  )
}

function CheckoutSummaryLineItem({ line }: { line: CheckoutSummaryLine }) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-background p-4">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-start">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <div className="font-semibold">{line.roomTypeName}</div>
            <Badge variant="success" className="w-fit">
              {line.roomCount} phòng
            </Badge>
          </div>
          <div className="text-sm text-muted-foreground">
            {line.roomCount} phòng x {line.nights} đêm
          </div>
        </div>
        <Badge variant="secondary" className="w-fit">
          {line.paymentLabel}
        </Badge>
      </div>

      <div className="grid gap-3 text-sm md:grid-cols-4">
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground">Chính sách hủy</span>
          <span className="font-medium">{line.cancellationPolicyName}</span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground">Loại giường</span>
          <div className="flex flex-col gap-1 font-medium">
            {line.bedLabels.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground">Sức chứa</span>
          <span className="font-medium">
            Tối đa {line.maxAdults ?? "-"} người lớn
          </span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-muted-foreground">Giá đêm đầu</span>
          <span className="font-medium">
            {money(line.dailyRates[0]?.price ?? 0, line.currency)}
          </span>
        </div>
      </div>

      <Separator />

      <div className="flex flex-col gap-2">
        <PriceLine label="Tiền phòng" value={money(line.roomsTotal, line.currency)} />
        <PriceLine label="VAT & phí" value={money(line.taxTotal, line.currency)} />
        <div className="flex justify-between gap-3 text-base font-semibold">
          <span>Tổng</span>
          <span>{money(line.totalAmount, line.currency)}</span>
        </div>
      </div>
    </div>
  )
}

function ContactForm({
  contact,
  onChange,
}: {
  contact: ContactState
  onChange: (contact: ContactState) => void
}) {
  return (
    <div className="flex flex-col gap-3">
      <LabeledInput label="Người liên hệ">
        <Input value={contact.contactName} onChange={(event) => onChange({ ...contact, contactName: event.target.value })} />
      </LabeledInput>
      <LabeledInput label="Email">
        <Input type="email" value={contact.contactEmail} onChange={(event) => onChange({ ...contact, contactEmail: event.target.value })} />
      </LabeledInput>
      <LabeledInput label="Số điện thoại">
        <Input type="tel" value={contact.contactPhone} onChange={(event) => onChange({ ...contact, contactPhone: event.target.value })} />
      </LabeledInput>
      <LabeledInput label="Yêu cầu đặc biệt">
        <Textarea value={contact.specialRequests} onChange={(event) => onChange({ ...contact, specialRequests: event.target.value })} />
      </LabeledInput>
    </div>
  )
}

function LabeledInput({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <Label>{label}</Label>
      {children}
    </div>
  )
}

function InfoLine({
  icon: Icon,
  label,
  value,
}: {
  icon: ComponentType<{ className?: string }>
  label: string
  value: string
}) {
  return (
    <div className="flex items-start gap-3 text-sm">
      <Icon className="mt-0.5 text-muted-foreground" />
      <div className="flex flex-col gap-1">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-medium">{value}</span>
      </div>
    </div>
  )
}

function PriceLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span>{value}</span>
    </div>
  )
}
