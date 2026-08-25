package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.dashboard.DashboardOverviewResponse;
import com.example.hotelmanagement.dto.revenue.MonthlyRevenuePoint;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.DashboardStayProjection;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DEFAULT_CURRENCY = "VND";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;
    private static final Set<BookingRoomStatus> BLOCKING_ROOM_STATUSES = Set.of(
            BookingRoomStatus.RESERVED,
            BookingRoomStatus.OCCUPIED
    );
    private static final Set<BookingStatus> ACTIVE_BOOKING_STATUSES = Set.of(
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN
    );

    private final BookingRoomRepository bookingRoomRepository;
    private final RoomRepository roomRepository;
    private final RevenueService revenueService;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final Clock clock;

    public DashboardService(
            BookingRoomRepository bookingRoomRepository,
            RoomRepository roomRepository,
            RevenueService revenueService,
            HotelSettingsRepository hotelSettingsRepository,
            Clock clock
    ) {
        this.bookingRoomRepository = bookingRoomRepository;
        this.roomRepository = roomRepository;
        this.revenueService = revenueService;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.DASHBOARD_READ)
    public DashboardOverviewResponse getOverview(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? today() : requestedDate;
        List<DashboardStayProjection> arrivals = bookingRoomRepository.findDashboardArrivals(date);
        List<DashboardStayProjection> departures = bookingRoomRepository.findDashboardDepartures(date);

        long totalRooms = roomRepository.countActiveOperationalRooms(RoomOperationalStatus.ACTIVE);
        DashboardOverviewResponse.OccupancyDay todayOccupancy = buildOccupancyDay(date, totalRooms);

        return new DashboardOverviewResponse(
                date,
                new DashboardOverviewResponse.BookingSummary(arrivals.size(), departures.size()),
                new DashboardOverviewResponse.RoomSummary(
                        totalRooms,
                        todayOccupancy.availableRooms(),
                        todayOccupancy.occupiedRooms(),
                        todayOccupancy.occupancyPercent()
                ),
                buildRevenueSummary(date),
                arrivals.stream().map(this::mapStay).toList(),
                departures.stream().map(this::mapStay).toList(),
                java.util.stream.IntStream.range(0, 7)
                        .mapToObj(date::plusDays)
                        .map(day -> buildOccupancyDay(day, totalRooms))
                        .toList()
        );
    }

    private DashboardOverviewResponse.OccupancyDay buildOccupancyDay(
            LocalDate date,
            long totalRooms
    ) {
        long availableRooms = roomRepository.countAvailableOnDate(
                date,
                date.plusDays(1),
                RoomOperationalStatus.ACTIVE,
                BLOCKING_ROOM_STATUSES
        );
        long occupiedRooms = roomRepository.countOccupiedOrReservedOnDate(
                date,
                date.plusDays(1),
                BLOCKING_ROOM_STATUSES,
                ACTIVE_BOOKING_STATUSES
        );
        return new DashboardOverviewResponse.OccupancyDay(
                date,
                totalRooms,
                availableRooms,
                occupiedRooms,
                percentage(occupiedRooms, totalRooms)
        );
    }

    private DashboardOverviewResponse.RevenueSummary buildRevenueSummary(LocalDate date) {
        YearMonth currentMonth = YearMonth.from(date);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        BigDecimal currentRevenue = sumRevenue(revenueService.getMonthlyRevenue(
                currentMonth.atDay(1),
                currentMonth.atEndOfMonth()
        ));
        BigDecimal previousRevenue = sumRevenue(revenueService.getMonthlyRevenue(
                previousMonth.atDay(1),
                previousMonth.atEndOfMonth()
        ));
        BigDecimal changePercent = previousRevenue.signum() == 0
                ? (currentRevenue.signum() == 0 ? BigDecimal.ZERO.setScale(MONEY_SCALE) : null)
                : currentRevenue.subtract(previousRevenue)
                        .multiply(HUNDRED)
                        .divide(previousRevenue, MONEY_SCALE, RoundingMode.HALF_UP);
        return new DashboardOverviewResponse.RevenueSummary(
                currentRevenue,
                previousRevenue,
                changePercent,
                DEFAULT_CURRENCY
        );
    }

    private BigDecimal sumRevenue(List<MonthlyRevenuePoint> monthlyRevenue) {
        return monthlyRevenue.stream()
                .map(MonthlyRevenuePoint::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private DashboardOverviewResponse.StayItem mapStay(DashboardStayProjection projection) {
        BigDecimal total = money(projection.getTotalAmount());
        BigDecimal paid = money(projection.getPaidAmount());
        BigDecimal refunded = money(projection.getRefundedAmount());
        return new DashboardOverviewResponse.StayItem(
                projection.getBookingPublicId(),
                projection.getBookingCode(),
                projection.getContactName(),
                projection.getContactPhone(),
                projection.getRoomNumber(),
                projection.getRoomTypeName(),
                projection.getCheckInDate(),
                projection.getCheckOutDate(),
                projection.getBookingStatus(),
                projection.getBookingRoomStatus(),
                total,
                paid,
                refunded,
                money(total.subtract(paid).add(refunded))
        );
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate today() {
        return OffsetDateTime.now(clock).atZoneSameInstant(resolveHotelZone()).toLocalDate();
    }

    private ZoneId resolveHotelZone() {
        String configured = hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY);
        if (configured == null || configured.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(configured);
        } catch (DateTimeException exception) {
            return FALLBACK_ZONE;
        }
    }
}
