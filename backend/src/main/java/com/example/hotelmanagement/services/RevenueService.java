package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.revenue.DailyRevenuePoint;
import com.example.hotelmanagement.dto.revenue.MonthlyRevenuePoint;
import com.example.hotelmanagement.dto.revenue.OccupancyMetrics;
import com.example.hotelmanagement.dto.revenue.SourceRevenueBreakdown;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRevenueProjection;
import com.example.hotelmanagement.repositories.BookingRoomNightRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.NightRevenueProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only dashboard/report queries (BE-7.5).
 *
 * <p>The ticket's ADR formula ("SUM / COUNT") is ambiguous about the denominator; taken literally
 * as {@code COUNT(DISTINCT stay_date)} it would divide total room revenue by the number of
 * calendar days in range, which is not a real ADR. DATABASE_DESIGN.md 9.4 is authoritative here
 * and computes ADR as {@code AVG(booking_room_nights.price)} — i.e. room revenue divided by the
 * number of room-nights actually sold — so that definition is used instead. RevPAR and occupancy
 * rate then share the same {@code available_room_nights} denominator (active rooms × days in
 * range), which keeps {@code RevPAR = ADR × occupancyRate} algebraically consistent with
 * {@code RevPAR = roomRevenue / availableRoomNights}.
 *
 * <p>"Doanh thu theo ngày/tháng", "doanh thu theo nguồn" and "commission OTA" are computed at the
 * booking level from {@code bookings.total_amount} / {@code rooms_total} /
 * {@code source_commission_percent_snapshot}, filtered to {@code status = CHECKED_OUT} exactly as
 * the ticket specifies, and bucketed by the booking's checkout date (converted to hotel-local
 * time). This is a coarser grain than the room-night metrics above (a multi-night booking's whole
 * revenue lands on its checkout day rather than being spread across stay dates) — a deliberate
 * simplification matching the ticket's literal formula rather than DATABASE_DESIGN 9.4's
 * per-stay-date model.
 */
@Service
@Transactional(readOnly = true)
public class RevenueService {

    private static final Set<BookingRoomStatus> SOLD_ROOM_STATUSES = Set.of(
            BookingRoomStatus.OCCUPIED, BookingRoomStatus.COMPLETED, BookingRoomStatus.MOVED_OUT
    );
    private static final Set<BookingStatus> REALIZED_BOOKING_STATUSES = Set.of(
            BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT
    );
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;
    private static final int RATE_INTERMEDIATE_SCALE = 6;
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookingRoomNightRepository bookingRoomNightRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final HotelSettingsRepository hotelSettingsRepository;

    public RevenueService(
            BookingRoomNightRepository bookingRoomNightRepository,
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            HotelSettingsRepository hotelSettingsRepository
    ) {
        this.bookingRoomNightRepository = bookingRoomNightRepository;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.hotelSettingsRepository = hotelSettingsRepository;
    }

    /** ADR, RevPAR and occupancy rate for the [from, to] range (both inclusive). */
    @PreAuthorize(PermissionExpressions.REVENUE_READ)
    public OccupancyMetrics getOccupancyMetrics(LocalDate from, LocalDate to) {
        validateRange(from, to);

        NightRevenueProjection aggregate = bookingRoomNightRepository.aggregateSoldNights(
                from, to, SOLD_ROOM_STATUSES, REALIZED_BOOKING_STATUSES
        );
        long occupiedRoomNights = aggregate.getNightsCount() == null ? 0L : aggregate.getNightsCount();
        BigDecimal roomRevenue = aggregate.getRoomRevenue() == null ? BigDecimal.ZERO : aggregate.getRoomRevenue();

        long activeRoomCount = roomRepository.countByDeletedAtIsNullAndIsActiveTrue();
        long daysInRange = ChronoUnit.DAYS.between(from, to) + 1;
        long availableRoomNights = activeRoomCount * daysInRange;

        BigDecimal adr = occupiedRoomNights == 0
                ? BigDecimal.ZERO
                : roomRevenue.divide(BigDecimal.valueOf(occupiedRoomNights), MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal occupancyRatePercent = availableRoomNights == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(occupiedRoomNights)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(availableRoomNights), RATE_INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal revPar = adr.multiply(occupancyRatePercent)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);

        return new OccupancyMetrics(adr, occupancyRatePercent, revPar, occupiedRoomNights, availableRoomNights);
    }

    /** Revenue recognized per calendar day (bookings checked out that day), hotel-local time. */
    @PreAuthorize(PermissionExpressions.REVENUE_READ)
    public List<DailyRevenuePoint> getDailyRevenue(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<LocalDate, List<BookingRevenueProjection>> byDay = fetchRealizedBookings(from, to).stream()
                .collect(Collectors.groupingBy(booking -> toHotelLocalDate(booking.getCheckedOutAt())));
        return byDay.entrySet().stream()
                .map(entry -> new DailyRevenuePoint(
                        entry.getKey(),
                        sumTotalAmount(entry.getValue()),
                        sumCommission(entry.getValue()),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(DailyRevenuePoint::date))
                .toList();
    }

    /** Revenue recognized per calendar month (bookings checked out that month), hotel-local time. */
    @PreAuthorize(PermissionExpressions.REVENUE_READ)
    public List<MonthlyRevenuePoint> getMonthlyRevenue(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<YearMonth, List<BookingRevenueProjection>> byMonth = fetchRealizedBookings(from, to).stream()
                .collect(Collectors.groupingBy(booking -> YearMonth.from(toHotelLocalDate(booking.getCheckedOutAt()))));
        return byMonth.entrySet().stream()
                .map(entry -> new MonthlyRevenuePoint(
                        entry.getKey(),
                        sumTotalAmount(entry.getValue()),
                        sumCommission(entry.getValue()),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(MonthlyRevenuePoint::month))
                .toList();
    }

    /** Revenue and OTA commission broken down by booking source. */
    @PreAuthorize(PermissionExpressions.REVENUE_READ)
    public List<SourceRevenueBreakdown> getRevenueBySource(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<String, List<BookingRevenueProjection>> bySource = fetchRealizedBookings(from, to).stream()
                .collect(Collectors.groupingBy(BookingRevenueProjection::getSourceCode));
        return bySource.values().stream()
                .map(bookings -> {
                    BookingRevenueProjection first = bookings.get(0);
                    return new SourceRevenueBreakdown(
                            first.getSourceCode(),
                            first.getSourceName(),
                            sumTotalAmount(bookings),
                            sumCommission(bookings),
                            bookings.size()
                    );
                })
                .sorted(Comparator.comparing(SourceRevenueBreakdown::revenue).reversed())
                .toList();
    }

    /** Total OTA commission owed for bookings checked out within the range. */
    @PreAuthorize(PermissionExpressions.REVENUE_READ)
    public BigDecimal getOtaCommissionTotal(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return sumCommission(fetchRealizedBookings(from, to));
    }

    private List<BookingRevenueProjection> fetchRealizedBookings(LocalDate from, LocalDate to) {
        ZoneId hotelZone = resolveHotelZone();
        OffsetDateTime fromInclusive = from.atStartOfDay(hotelZone).toOffsetDateTime();
        OffsetDateTime toExclusive = to.plusDays(1).atStartOfDay(hotelZone).toOffsetDateTime();
        return bookingRepository.findRevenueRecognizedBookings(BookingStatus.CHECKED_OUT, fromInclusive, toExclusive);
    }

    private LocalDate toHotelLocalDate(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(resolveHotelZone()).toLocalDate();
    }

    private BigDecimal sumTotalAmount(List<BookingRevenueProjection> bookings) {
        return bookings.stream()
                .map(BookingRevenueProjection::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCommission(List<BookingRevenueProjection> bookings) {
        return bookings.stream()
                .map(this::commissionOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal commissionOf(BookingRevenueProjection booking) {
        BigDecimal roomsTotal = booking.getRoomsTotal() == null ? BigDecimal.ZERO : booking.getRoomsTotal();
        BigDecimal commissionPercent = booking.getSourceCommissionPercentSnapshot() == null
                ? BigDecimal.ZERO
                : booking.getSourceCommissionPercentSnapshot();
        return roomsTotal.multiply(commissionPercent).divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessValidationException("Both 'from' and 'to' dates are required");
        }
        if (to.isBefore(from)) {
            throw new BusinessValidationException("'to' date cannot be before 'from' date");
        }
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
