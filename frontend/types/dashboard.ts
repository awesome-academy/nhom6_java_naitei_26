export type BookingStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CHECKED_OUT"
  | "CANCELLED"
  | "NO_SHOW"
  | "EXPIRED"

export type BookingRoomStatus =
  | "RESERVED"
  | "OCCUPIED"
  | "COMPLETED"
  | "RELEASED"
  | "MOVED_OUT"

export interface DashboardStayItem {
  bookingPublicId: string
  bookingCode: string
  contactName: string
  contactPhone: string | null
  roomNumber: string
  roomTypeName: string
  checkInDate: string
  checkOutDate: string
  bookingStatus: BookingStatus
  bookingRoomStatus: BookingRoomStatus
  totalAmount: number
  paidAmount: number
  refundedAmount: number
  balanceDue: number
}

export interface DashboardOccupancyDay {
  date: string
  totalRooms: number
  availableRooms: number
  occupiedRooms: number
  occupancyPercent: number
}

export interface DashboardOverview {
  date: string
  bookingSummary: {
    arrivalsCount: number
    departuresCount: number
  }
  roomSummary: {
    totalRooms: number
    availableRooms: number
    occupiedRooms: number
    occupancyPercent: number
  }
  revenueSummary: {
    currentMonthRevenue: number
    previousMonthRevenue: number
    changePercent: number | null
    currency: string
  }
  arrivals: DashboardStayItem[]
  departures: DashboardStayItem[]
  occupancyNext7Days: DashboardOccupancyDay[]
}
