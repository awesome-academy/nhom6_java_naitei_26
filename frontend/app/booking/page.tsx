"use client"

import {
  useEffect,
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
  eachDayOfInterval,
  endOfMonth,
  format,
  getDay,
  isBefore,
  isSameDay,
  isWithinInterval,
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
  ChevronLeft,
  ChevronRight,
  CreditCard,
  Heart,
  ImageIcon,
  Info,
  Loader2,
  MapPin,
  Minus,
  Plane,
  Plus,
  Search,
  Share2,
  ShieldCheck,
  Star,
  Train,
  Users,
  Wifi,
  X,
} from "lucide-react"
import { toast } from "sonner"

import { SiteHeader } from "@/components/auth/site-header"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
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
  getBookingRooms,
  getBookingRoomTypes,
  getCancellationPolicies,
} from "@/lib/api/booking"
import { useAuth } from "@/lib/auth-context"
import { cn } from "@/lib/utils"
import type {
  Booking,
  BookingRoomItem,
  CancellationPolicy,
  PriceCalculation,
} from "@/types/booking"
import type { Room } from "@/types/room"
import type { RoomType } from "@/types/room-type"

type SearchState = {
  destination: string
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
  availableRooms: Room[]
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

const today = startOfToday()
const initialSearch: SearchState = {
  destination: "TripStay Hotel",
  checkInDate: format(addDays(today, 1), "yyyy-MM-dd"),
  checkOutDate: format(addDays(today, 2), "yyyy-MM-dd"),
  rooms: 1,
  adults: 2,
  children: 0,
}

const popularDestinations = [
  "TP. Hồ Chí Minh",
  "Đà Nẵng",
  "Hà Nội",
  "Nha Trang",
  "Đà Lạt",
  "Vũng Tàu",
  "Đảo Phú Quốc",
  "Phan Thiết",
]

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
  if (roomType.beds.length === 0) return `${roomType.bedCount} giường`
  return roomType.beds
    .map((bed) => `${bed.quantity} ${bedTypeLabel[bed.bedType] ?? bed.bedType}`)
    .join(", ")
}

function distributeGuests(search: SearchState, roomCount: number) {
  return Array.from({ length: roomCount }, (_, index) => {
    const baseAdults = Math.floor(search.adults / roomCount)
    const extraAdults = index < search.adults % roomCount ? 1 : 0
    const baseChildren = Math.floor(search.children / roomCount)
    const extraChildren = index < search.children % roomCount ? 1 : 0

    return {
      adults: Math.max(1, baseAdults + extraAdults),
      children: baseChildren + extraChildren,
    }
  })
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

export default function BookingPage() {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth()
  const [search, setSearch] = useState<SearchState>(initialSearch)
  const [contact, setContact] = useState<ContactState>({
    contactName: "",
    contactEmail: "",
    contactPhone: "",
    specialRequests: "",
  })
  const [rooms, setRooms] = useState<Room[]>([])
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [policies, setPolicies] = useState<CancellationPolicy[]>([])
  const [availableRoomIds, setAvailableRoomIds] = useState<Set<number>>(new Set())
  const [selectedQuantities, setSelectedQuantities] = useState<Record<string, number>>({})
  const [selectedRoomIds, setSelectedRoomIds] = useState<number[]>([])
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
  const selectedPolicy = policies.find((policy) => policy.isDefault) ?? policies[0]

  const roomTypeOptions = useMemo<RoomTypeOption[]>(() => {
    return roomTypes
      .filter((roomType) => roomType.isActive)
      .map((roomType) => {
        const availableRooms = rooms.filter(
          (room) =>
            room.isActive &&
            room.roomTypeCode === roomType.code &&
            availableRoomIds.has(room.roomId)
        )
        return { roomType, availableRooms }
      })
      .sort((left, right) => left.roomType.sortOrder - right.roomType.sortOrder)
  }, [availableRoomIds, rooms, roomTypes])

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

  const selectedCount = Object.values(selectedQuantities).reduce((sum, quantity) => sum + quantity, 0)
  const selectedOptions = roomTypeOptions.filter((option) => selectedQuantities[option.roomType.code] > 0)
  const cheapestRoomType = roomTypes.reduce<RoomType | null>((best, current) => {
    if (!best) return current
    return Number(current.basePrice) < Number(best.basePrice) ? current : best
  }, null)

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
        const [roomData, roomTypeData, policyData, availability] = await Promise.all([
          getBookingRooms(),
          getBookingRoomTypes(),
          getCancellationPolicies(),
          getAvailability(search.checkInDate, search.checkOutDate),
        ])
        if (ignore) return
        setRooms(roomData)
        setRoomTypes(roomTypeData)
        setPolicies(policyData.filter((policy) => policy.isActive))
        setAvailableRoomIds(new Set(Object.values(availability).flat().map((roomId) => Number(roomId))))
      } catch (loadError) {
        if (!ignore) {
          setError(getApiErrorMessage(loadError, "Không thể tải dữ liệu khách sạn"))
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
  }, [accessMessage, authLoading, isAuthenticated, search.checkInDate, search.checkOutDate, user])

  function resetSelection(nextSearch: SearchState) {
    setSearch(nextSearch)
    setSelectedQuantities({})
    setSelectedRoomIds([])
    setQuote(null)
    setCheckoutOpen(false)
  }

  async function runSearch() {
    setError("")

    if (nights < 1) {
      setError("Ngày trả phòng phải sau ngày nhận phòng.")
      return
    }

    if (search.adults < search.rooms) {
      setError("Mỗi phòng cần tối thiểu 1 người lớn.")
      return
    }

    setSearching(true)
    setSelectedQuantities({})
    setSelectedRoomIds([])
    setQuote(null)
    setCheckoutOpen(false)

    try {
      const availability = await getAvailability(search.checkInDate, search.checkOutDate)
      const ids = Object.values(availability)
        .flat()
        .map((roomId) => Number(roomId))
      setAvailableRoomIds(new Set(ids))
      document.getElementById("choose-room")?.scrollIntoView({ behavior: "smooth", block: "start" })
    } catch (searchError) {
      setAvailableRoomIds(new Set())
      setError(getApiErrorMessage(searchError, "Không thể kiểm tra phòng còn trống"))
    } finally {
      setSearching(false)
    }
  }

  function updateSelectedQuantity(option: RoomTypeOption, quantity: number) {
    const safeQuantity = Math.max(0, Math.min(quantity, option.availableRooms.length))
    setQuote(null)
    setSelectedRoomIds([])
    setSelectedQuantities((current) => {
      const next = { ...current }
      if (safeQuantity === 0) {
        delete next[option.roomType.code]
      } else {
        next[option.roomType.code] = safeQuantity
      }
      return next
    })
  }

  async function calculateSelectedRooms() {
    setError("")
    setCalculating(true)

    const plannedRoomIds = selectedOptions.flatMap((option) =>
      option.availableRooms
        .slice(0, selectedQuantities[option.roomType.code] ?? 0)
        .map((room) => room.roomId)
    )

    if (plannedRoomIds.length === 0) {
      setError("Vui lòng thêm ít nhất một loại phòng.")
      setCalculating(false)
      return
    }

    const guestDistribution = distributeGuests(
      { ...search, rooms: plannedRoomIds.length },
      plannedRoomIds.length
    )

    try {
      const calculations = await Promise.all(
        plannedRoomIds.map((roomId, index) =>
          calculateBookingPrice({
            roomId,
            checkInDate: search.checkInDate,
            checkOutDate: search.checkOutDate,
            adults: guestDistribution[index].adults,
            children: guestDistribution[index].children,
          })
        )
      )
      setSelectedRoomIds(plannedRoomIds)
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
      setSelectedRoomIds([])
      setError(getApiErrorMessage(quoteError, "Không thể tính giá cho các phòng đã chọn"))
    } finally {
      setCalculating(false)
    }
  }

  async function submitBooking() {
    if (selectedRoomIds.length === 0 || !quote) {
      setError("Vui lòng bấm tiếp tục thanh toán để tính giá trước.")
      return
    }

    if (!selectedPolicy) {
      setError("Backend chưa có chính sách hủy hoạt động.")
      return
    }

    if (!contact.contactName.trim() || !contact.contactEmail.trim()) {
      setError("Vui lòng nhập tên và email người liên hệ.")
      return
    }

    const guestDistribution = distributeGuests(
      { ...search, rooms: selectedRoomIds.length },
      selectedRoomIds.length
    )
    const bookingRooms: BookingRoomItem[] = selectedRoomIds.map((roomId, index) => ({
      roomId,
      checkInDate: search.checkInDate,
      checkOutDate: search.checkOutDate,
      adults: guestDistribution[index].adults,
      children: guestDistribution[index].children,
    }))

    setCreatingBooking(true)
    setError("")

    try {
      const created = await createBooking({
        cancellationPolicyCode: selectedPolicy.code,
        contactName: contact.contactName.trim(),
        contactEmail: contact.contactEmail.trim(),
        contactPhone: contact.contactPhone.trim() || undefined,
        specialRequests: contact.specialRequests.trim() || undefined,
        rooms: bookingRooms,
      })
      setBooking(created)
      toast.success("Đã giữ phòng tạm thời")
    } catch (bookingError) {
      setError(getApiErrorMessage(bookingError, "Không thể tạo booking"))
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
          <div className="flex size-16 items-center justify-center rounded-full bg-primary text-primary-foreground">
            <Check />
          </div>
          <Badge variant="confirmed">Booking PENDING</Badge>
          <h1 className="font-serif text-4xl tracking-tight">Đã giữ phòng thành công.</h1>
          <p className="text-muted-foreground">
            Mã đặt phòng <span className="font-semibold text-foreground">{booking.bookingCode}</span>.
            Phòng được giữ đến{" "}
            <span className="font-semibold text-foreground">
              {new Date(booking.holdExpiresAt).toLocaleString("vi-VN")}
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
    <div className="min-h-screen bg-background pb-28">
      <SiteHeader />
      <HotelSearchBar
        search={search}
        loading={searching || loadingCatalog}
        minNightPrice={cheapestRoomType ? Number(cheapestRoomType.basePrice) : 0}
        onChange={resetSelection}
        onSearch={runSearch}
      />

      <main className="mx-auto flex max-w-7xl flex-col gap-8 px-6 py-8 lg:px-8">
        {error && (
          <Alert variant="destructive">
            <AlertTitle>Không thể tiếp tục</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <HotelHeader />
        <HotelGallery images={galleryImages} loading={loadingCatalog} />
        <HotelDetails roomTypes={roomTypes} policies={policies} cheapestRoomType={cheapestRoomType} search={search} />

        <section id="choose-room" className="scroll-mt-28">
          <div className="mb-4 flex flex-col gap-2">
            <h2 className="text-2xl font-bold tracking-tight">Chọn phòng</h2>
            <p className="text-muted-foreground">
              Chọn loại phòng và số lượng. Hệ thống chỉ tính tiền khi bạn bấm thanh toán phía dưới.
            </p>
          </div>

          <RoomTypeList
            options={roomTypeOptions}
            loading={loadingCatalog || searching}
            selectedQuantities={selectedQuantities}
            onQuantityChange={updateSelectedQuantity}
          />
        </section>
      </main>

      {selectedCount > 0 && (
        <BottomCheckoutBar
          selectedCount={selectedCount}
          search={search}
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
        policy={selectedPolicy}
        creating={creatingBooking}
        onContactChange={setContact}
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
          <div className="flex text-primary">
            {Array.from({ length: 5 }).map((_, index) => (
              <Star key={index} className="fill-current" />
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
  policies,
  cheapestRoomType,
  search,
}: {
  roomTypes: RoomType[]
  policies: CancellationPolicy[]
  cheapestRoomType: RoomType | null
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
        roomType={cheapestRoomType}
        policy={policies[0]}
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
  roomType,
  policy,
  search,
}: {
  roomType: RoomType | null
  policy?: CancellationPolicy
  search: SearchState
}) {
  const estimate = roomType ? Number(roomType.basePrice) * search.rooms * getNights(search) : 0

  return (
    <Card className="h-fit overflow-hidden shadow-xl lg:sticky lg:top-36">
      <div className="bg-cyan-50 px-6 py-4 text-xl font-bold text-cyan-800">
        Giá tốt nhất của hôm nay!
      </div>
      <CardContent className="flex flex-col gap-4 p-6">
        <div>
          <div className="text-3xl font-bold text-primary">{money(roomType?.basePrice ?? 0, roomType?.currency ?? "VND")}</div>
          <div className="text-sm text-muted-foreground">
            Tổng giá: <span className="font-semibold text-foreground">{money(estimate, roomType?.currency ?? "VND")}</span>
          </div>
          <div className="text-sm text-muted-foreground">
            {search.rooms} phòng × {getNights(search)} đêm bao gồm thuế & phí sau khi tính giá
          </div>
        </div>
        <div className="font-semibold">{roomType?.name ?? "Chọn phòng"}</div>
        <InfoPill icon={BedDouble} label={roomType ? getBedSummary(roomType) : "Chưa có loại phòng"} />
        <InfoPill icon={Ban} label={policy?.name ?? "Chính sách theo booking"} />
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
    <div className="sticky top-16 z-40 border-b bg-background/95 px-4 py-3 shadow-sm backdrop-blur">
      <div className="mx-auto flex max-w-7xl rounded-xl border-2 border-primary bg-background p-1">
        <DestinationPicker search={search} onChange={onChange} />
        <SearchDivider />
        <DateRangePicker search={search} minNightPrice={minNightPrice} onChange={onChange} />
        <SearchDivider />
        <GuestPicker search={search} onChange={onChange} />
        <Button className="h-12 rounded-lg px-6 text-base" onClick={onSearch} disabled={loading}>
          {loading ? <Loader2 data-icon="inline-start" className="animate-spin" /> : <Search data-icon="inline-start" />}
          Tìm
        </Button>
      </div>
    </div>
  )
}

function SearchDivider() {
  return <Separator orientation="vertical" className="mx-1 h-12" />
}

function DestinationPicker({
  search,
  onChange,
}: {
  search: SearchState
  onChange: (search: SearchState) => void
}) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button className="flex h-12 min-w-[280px] flex-1 items-center gap-3 rounded-lg px-4 text-left hover:bg-muted">
          <MapPin />
          <span className="truncate text-base font-semibold">{search.destination}</span>
          <X className="ml-auto text-muted-foreground" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[640px] p-6">
        <div className="flex flex-col gap-5">
          <Label>Tìm kiếm gần đây</Label>
          <button
            className="flex items-center justify-between rounded-lg p-2 text-left hover:bg-muted"
            onClick={() => onChange({ ...search, destination: "TP. Hồ Chí Minh" })}
          >
            <span>TP. Hồ Chí Minh</span>
            <span className="text-muted-foreground">{displayDate(search.checkInDate)} - {displayDate(search.checkOutDate)}</span>
          </button>
          <Separator />
          <Label>Điểm đến phổ biến</Label>
          <div className="grid grid-cols-4 gap-3">
            {popularDestinations.map((destination) => (
              <button
                key={destination}
                className="rounded-lg p-2 text-left hover:bg-muted"
                onClick={() => onChange({ ...search, destination })}
              >
                {destination}
              </button>
            ))}
          </div>
        </div>
      </PopoverContent>
    </Popover>
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
        <button className="flex h-12 min-w-[420px] items-center gap-4 rounded-lg px-4 hover:bg-muted">
          <CalendarDays />
          <span className="text-base font-semibold">{headerDate(search.checkInDate)}</span>
          <span className="text-base font-semibold">-</span>
          <span className="text-base font-semibold">{headerDate(search.checkOutDate)}</span>
          <Badge variant="secondary">{nights} đêm</Badge>
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[760px] p-6">
        <div className="flex items-center justify-between">
          <Button variant="ghost" size="icon" onClick={() => setCursorMonth(addMonths(cursorMonth, -1))}>
            <ChevronLeft />
          </Button>
          <div className="grid flex-1 grid-cols-2 gap-8">
            {[cursorMonth, addMonths(cursorMonth, 1)].map((month) => (
              <MonthCalendar
                key={month.toISOString()}
                month={month}
                search={search}
                minNightPrice={minNightPrice}
                onSelect={selectDate}
              />
            ))}
          </div>
          <Button variant="ghost" size="icon" onClick={() => setCursorMonth(addMonths(cursorMonth, 1))}>
            <ChevronRight />
          </Button>
        </div>
        <div className="mt-6 flex items-center justify-between text-sm text-muted-foreground">
          <span>Di chuột để xem giá từng ngày</span>
          <span>{headerDate(search.checkInDate)} - {headerDate(search.checkOutDate)} ({nights} đêm)</span>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function MonthCalendar({
  month,
  search,
  minNightPrice,
  onSelect,
}: {
  month: Date
  search: SearchState
  minNightPrice: number
  onSelect: (date: Date) => void
}) {
  const days = eachDayOfInterval({ start: startOfMonth(month), end: endOfMonth(month) })
  const offset = (getDay(startOfMonth(month)) + 6) % 7
  const checkIn = parseISO(search.checkInDate)
  const checkOut = parseISO(search.checkOutDate)

  return (
    <div className="flex flex-col gap-4">
      <div className="text-center text-lg font-bold">tháng {format(month, "M, yyyy")}</div>
      <div className="grid grid-cols-7 gap-1 text-center text-sm font-semibold">
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((day) => (
          <span key={day} className={cn(day === "T7" || day === "CN" ? "text-primary" : "text-foreground")}>{day}</span>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-1">
        {Array.from({ length: offset }).map((_, index) => <div key={index} />)}
        {days.map((day) => {
          const disabled = isBefore(day, today)
          const selected = isSameDay(day, checkIn) || isSameDay(day, checkOut)
          const ranged = search.checkOutDate !== search.checkInDate && isWithinInterval(day, { start: checkIn, end: checkOut })

          return (
            <button
              key={day.toISOString()}
              type="button"
              disabled={disabled}
              onClick={() => onSelect(day)}
              className={cn(
                "flex h-16 flex-col items-center justify-center rounded-lg text-sm hover:bg-muted disabled:cursor-not-allowed disabled:opacity-35",
                ranged && "bg-primary/10",
                selected && "bg-primary text-primary-foreground hover:bg-primary"
              )}
            >
              <span className="text-base font-bold">{format(day, "d")}</span>
              {!disabled && <span className="text-xs opacity-80">{compactMoney(minNightPrice)}</span>}
            </button>
          )
        })}
      </div>
    </div>
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
        <button className="flex h-12 min-w-[300px] items-center gap-3 rounded-lg px-4 hover:bg-muted">
          <Users />
          <span className="text-base font-semibold">
            {search.rooms} phòng, {search.adults} Người Lớn, {search.children} Trẻ Em
          </span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[360px] p-6">
        <div className="flex flex-col gap-6">
          <CounterRow
            label="Phòng"
            value={search.rooms}
            min={1}
            onChange={(rooms) => onChange({ ...search, rooms, adults: Math.max(search.adults, rooms) })}
          />
          <CounterRow
            label="Người lớn"
            description="18+ tuổi"
            value={search.adults}
            min={search.rooms}
            onChange={(adults) => onChange({ ...search, adults })}
          />
          <CounterRow
            label="Trẻ em"
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
  onChange,
}: {
  label: string
  description?: string
  value: number
  min: number
  onChange: (value: number) => void
}) {
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
        <Button type="button" variant="outline" size="icon" onClick={() => onChange(value + 1)}>
          <Plus />
        </Button>
      </div>
    </div>
  )
}

function RoomTypeList({
  options,
  loading,
  selectedQuantities,
  onQuantityChange,
}: {
  options: RoomTypeOption[]
  loading: boolean
  selectedQuantities: Record<string, number>
  onQuantityChange: (option: RoomTypeOption, quantity: number) => void
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
        const availableCount = option.availableRooms.length
        const quantity = selectedQuantities[roomType.code] ?? 0
        const primaryImage = roomType.images.find((image) => image.isPrimary) ?? roomType.images[0]

        return (
          <Card key={roomType.code} className={cn(quantity > 0 && "border-green-500")}>
            <CardContent className="grid gap-0 p-0 lg:grid-cols-[430px_1fr]">
              <div className="border-r p-5">
                <GalleryTile
                  item={primaryImage ? { url: primaryImage.downloadUrl, alt: primaryImage.altText } : undefined}
                  className="mb-5 h-56 rounded-xl"
                  overlay={`${roomType.images.length || 1}`}
                />
                <div className="flex items-start justify-between gap-3">
                  <h3 className="text-xl font-bold">{roomType.name}</h3>
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

              <div className="grid lg:grid-cols-2">
                <RateOptionCard
                  title="Giá thấp nhất hôm nay"
                  roomType={roomType}
                  availableCount={availableCount}
                  quantity={quantity}
                  variant="best"
                  onQuantityChange={(nextQuantity) => onQuantityChange(option, nextQuantity)}
                />
                <RateOptionCard
                  title="Chỉ tiền phòng"
                  roomType={roomType}
                  availableCount={availableCount}
                  quantity={quantity}
                  priceMultiplier={1.12}
                  onQuantityChange={(nextQuantity) => onQuantityChange(option, nextQuantity)}
                />
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

function RateOptionCard({
  title,
  roomType,
  availableCount,
  quantity,
  variant,
  priceMultiplier = 1,
  onQuantityChange,
}: {
  title: string
  roomType: RoomType
  availableCount: number
  quantity: number
  variant?: "best"
  priceMultiplier?: number
  onQuantityChange: (quantity: number) => void
}) {
  const unitPrice = Number(roomType.basePrice) * priceMultiplier

  return (
    <div className={cn("flex min-h-[360px] flex-col justify-between border-r p-5", quantity > 0 && "bg-green-50/70")}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <Badge variant={variant === "best" ? "success" : "outline"}>{title}</Badge>
          <Info className="text-muted-foreground" />
        </div>
        <InfoPill icon={Ban} label="Không hoàn tiền" />
        <InfoPill icon={CreditCard} label={variant === "best" ? "Thanh toán trước trực tuyến" : "Thanh toán tại khách sạn"} />
        <InfoPill icon={Users} label={`Tối đa ${roomType.maxAdults} người lớn`} />
      </div>

      <div className="flex flex-col gap-3">
        <div className="text-2xl font-bold text-primary">{money(unitPrice, roomType.currency)}</div>
        <div className="text-sm text-muted-foreground">Còn {availableCount} phòng có thể chọn</div>
        {availableCount <= 3 && availableCount > 0 && (
          <div className="font-semibold text-destructive">{availableCount} cuối của chúng tôi!</div>
        )}
        {quantity > 0 ? (
          <div className="flex items-center justify-between gap-3 rounded-lg border border-green-500 bg-green-100 p-2">
            <Button type="button" variant="outline" size="icon" onClick={() => onQuantityChange(quantity - 1)}>
              <Minus />
            </Button>
            <div className="text-center font-semibold text-green-700">
              Đã chọn {quantity}
            </div>
            <Button type="button" variant="outline" size="icon" disabled={quantity >= availableCount} onClick={() => onQuantityChange(quantity + 1)}>
              <Plus />
            </Button>
          </div>
        ) : (
          <Button disabled={availableCount < 1} onClick={() => onQuantityChange(1)}>
            Thêm
          </Button>
        )}
      </div>
    </div>
  )
}

function BottomCheckoutBar({
  selectedCount,
  search,
  calculating,
  onContinue,
}: {
  selectedCount: number
  search: SearchState
  calculating: boolean
  onContinue: () => void
}) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-50 border-t bg-background/95 px-4 py-4 shadow-2xl backdrop-blur">
      <div className="mx-auto flex max-w-4xl items-center justify-between gap-6 rounded-2xl border bg-card p-4 shadow-xl">
        <div>
          <div className="text-sm text-muted-foreground">Đã thêm {selectedCount} phòng</div>
          <div className="text-lg font-bold">
            {displayDate(search.checkInDate)} - {displayDate(search.checkOutDate)} · {getNights(search)} đêm
          </div>
          <div className="text-sm text-muted-foreground">Bấm tiếp tục để backend tính giá chính xác.</div>
        </div>
        <Button size="lg" onClick={onContinue} disabled={calculating}>
          {calculating && <Loader2 data-icon="inline-start" className="animate-spin" />}
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
  policy,
  creating,
  onContactChange,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  search: SearchState
  contact: ContactState
  quote: Quote | null
  policy?: CancellationPolicy
  creating: boolean
  onContactChange: (contact: ContactState) => void
  onSubmit: () => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Thanh toán đặt phòng</DialogTitle>
          <DialogDescription>
            Giá bên dưới được tính từ backend theo từng phòng vật lý được chọn nội bộ.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-6 md:grid-cols-[1fr_320px]">
          <ContactForm contact={contact} onChange={onContactChange} />
          <div className="flex flex-col gap-4 rounded-xl border p-4">
            <InfoLine icon={CalendarDays} label="Ngày" value={`${displayDate(search.checkInDate)} - ${displayDate(search.checkOutDate)} (${getNights(search)} đêm)`} />
            <InfoLine icon={Users} label="Khách" value={`${search.adults} người lớn, ${search.children} trẻ em`} />
            {policy && <InfoLine icon={ShieldCheck} label="Chính sách" value={policy.name} />}
            <Separator />
            {quote && (
              <>
                <PriceLine label="Tiền phòng" value={money(quote.roomsTotal, quote.currency)} />
                <PriceLine label="Thuế" value={money(quote.taxTotal, quote.currency)} />
                <div className="flex justify-between gap-3 text-base font-semibold">
                  <span>Tổng cộng</span>
                  <span className="text-primary">{money(quote.totalAmount, quote.currency)}</span>
                </div>
              </>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Quay lại chọn phòng</Button>
          <Button onClick={onSubmit} disabled={!quote || creating}>
            {creating && <Loader2 data-icon="inline-start" className="animate-spin" />}
            Giữ phòng
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
        <Input value={contact.contactPhone} onChange={(event) => onChange({ ...contact, contactPhone: event.target.value })} />
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
