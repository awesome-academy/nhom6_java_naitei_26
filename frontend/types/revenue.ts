export interface RevenueRange {
  from: string
  to: string
}

export interface OccupancyMetrics {
  adr: number
  occupancyRatePercent: number
  revPar: number
  occupiedRoomNights: number
  availableRoomNights: number
}

export interface DailyRevenuePoint {
  date: string
  revenue: number
  otaCommission: number
  bookingCount: number
}

export interface MonthlyRevenuePoint {
  month: string
  revenue: number
  otaCommission: number
  bookingCount: number
}

export interface SourceRevenueBreakdown {
  sourceCode: string
  sourceName: string
  revenue: number
  otaCommission: number
  bookingCount: number
}

export interface RoomTypeRevenueBreakdown {
  roomTypeCode: string
  roomTypeName: string
  revenue: number
  roomNights: number
  adr: number
}

export interface RevenueReportData {
  occupancy: OccupancyMetrics
  daily: DailyRevenuePoint[]
  monthly: MonthlyRevenuePoint[]
  bySource: SourceRevenueBreakdown[]
  byRoomType: RoomTypeRevenueBreakdown[]
}
