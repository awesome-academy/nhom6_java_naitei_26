package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.revenue.DailyRevenuePoint;
import com.example.hotelmanagement.dto.revenue.MonthlyRevenuePoint;
import com.example.hotelmanagement.dto.revenue.OccupancyMetrics;
import com.example.hotelmanagement.dto.revenue.RoomTypeRevenueBreakdown;
import com.example.hotelmanagement.dto.revenue.SourceRevenueBreakdown;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRevenueProjection;
import com.example.hotelmanagement.repositories.BookingRoomNightRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.NightRevenueProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomTypeRevenueProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 10);

    @Mock
    private BookingRoomNightRepository bookingRoomNightRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    private RevenueService revenueService;

    @BeforeEach
    void setUp() {
        revenueService = new RevenueService(
                bookingRoomNightRepository, bookingRepository, roomRepository, hotelSettingsRepository
        );
    }

    @Test
    void getOccupancyMetricsComputesAdrOccupancyAndRevPar() {
        when(bookingRoomNightRepository.aggregateSoldNights(eq(FROM), eq(TO), any(), any()))
                .thenReturn(nightRevenue(new BigDecimal("45000000.00"), 50L));
        when(roomRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(20L);

        OccupancyMetrics metrics = revenueService.getOccupancyMetrics(FROM, TO);

        // 10-day range x 20 active rooms = 200 available room-nights; 50 sold.
        assertThat(metrics.occupiedRoomNights()).isEqualTo(50L);
        assertThat(metrics.availableRoomNights()).isEqualTo(200L);
        assertThat(metrics.adr()).isEqualByComparingTo("900000.00");
        assertThat(metrics.occupancyRatePercent()).isEqualByComparingTo("25.00");
        assertThat(metrics.revPar()).isEqualByComparingTo("225000.00");
    }

    @Test
    void getOccupancyMetricsHandlesNoSoldNightsWithoutDivideByZero() {
        when(bookingRoomNightRepository.aggregateSoldNights(eq(FROM), eq(TO), any(), any()))
                .thenReturn(nightRevenue(BigDecimal.ZERO, 0L));
        when(roomRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(20L);

        OccupancyMetrics metrics = revenueService.getOccupancyMetrics(FROM, TO);

        assertThat(metrics.adr()).isEqualByComparingTo("0.00");
        assertThat(metrics.occupancyRatePercent()).isEqualByComparingTo("0.00");
        assertThat(metrics.revPar()).isEqualByComparingTo("0.00");
    }

    @Test
    void getOccupancyMetricsHandlesNoActiveRoomsWithoutDivideByZero() {
        when(bookingRoomNightRepository.aggregateSoldNights(eq(FROM), eq(TO), any(), any()))
                .thenReturn(nightRevenue(BigDecimal.ZERO, 0L));
        when(roomRepository.countByDeletedAtIsNullAndIsActiveTrue()).thenReturn(0L);

        OccupancyMetrics metrics = revenueService.getOccupancyMetrics(FROM, TO);

        assertThat(metrics.availableRoomNights()).isZero();
        assertThat(metrics.occupancyRatePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void getOccupancyMetricsRejectsInvalidRange() {
        assertThatThrownBy(() -> revenueService.getOccupancyMetrics(TO, FROM))
                .isInstanceOf(BusinessValidationException.class);
        assertThatThrownBy(() -> revenueService.getOccupancyMetrics(null, TO))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void getDailyRevenueGroupsByHotelLocalCheckoutDate() {
        stubHotelZone();
        stubRealizedBookings(threeAugustBookings());

        List<DailyRevenuePoint> points = revenueService.getDailyRevenue(FROM, TO);

        assertThat(points).hasSize(2);
        DailyRevenuePoint day1 = points.get(0);
        assertThat(day1.date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(day1.revenue()).isEqualByComparingTo("1000000.00");
        assertThat(day1.otaCommission()).isEqualByComparingTo("80000.00");
        assertThat(day1.bookingCount()).isEqualTo(1);

        DailyRevenuePoint day2 = points.get(1);
        assertThat(day2.date()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(day2.revenue()).isEqualByComparingTo("2500000.00");
        assertThat(day2.otaCommission()).isEqualByComparingTo("100000.00");
        assertThat(day2.bookingCount()).isEqualTo(2);
    }

    @Test
    void getMonthlyRevenueGroupsAcrossMonthBoundary() {
        stubHotelZone();
        List<BookingRevenueProjection> bookings = new java.util.ArrayList<>(threeAugustBookings());
        bookings.add(bookingRevenue(
                OffsetDateTime.parse("2026-09-05T10:00:00Z"),
                new BigDecimal("300000.00"), new BigDecimal("300000.00"), BigDecimal.ZERO, "A", "Source A"
        ));
        stubRealizedBookings(bookings);

        List<MonthlyRevenuePoint> points = revenueService.getMonthlyRevenue(FROM, LocalDate.of(2026, 9, 30));

        assertThat(points).hasSize(2);
        assertThat(points.get(0).month()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(points.get(0).revenue()).isEqualByComparingTo("3500000.00");
        assertThat(points.get(0).otaCommission()).isEqualByComparingTo("180000.00");
        assertThat(points.get(0).bookingCount()).isEqualTo(3);

        assertThat(points.get(1).month()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(points.get(1).revenue()).isEqualByComparingTo("300000.00");
        assertThat(points.get(1).bookingCount()).isEqualTo(1);
    }

    @Test
    void getRevenueBySourceSumsAndSortsBySourceDescending() {
        stubHotelZone();
        stubRealizedBookings(threeAugustBookings());

        List<SourceRevenueBreakdown> breakdown = revenueService.getRevenueBySource(FROM, TO);

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown.get(0).sourceCode()).isEqualTo("B");
        assertThat(breakdown.get(0).revenue()).isEqualByComparingTo("2000000.00");
        assertThat(breakdown.get(0).otaCommission()).isEqualByComparingTo("0.00");
        assertThat(breakdown.get(0).bookingCount()).isEqualTo(1);

        assertThat(breakdown.get(1).sourceCode()).isEqualTo("A");
        assertThat(breakdown.get(1).revenue()).isEqualByComparingTo("1500000.00");
        assertThat(breakdown.get(1).otaCommission()).isEqualByComparingTo("180000.00");
        assertThat(breakdown.get(1).bookingCount()).isEqualTo(2);
    }

    @Test
    void getOtaCommissionTotalSumsAcrossAllBookings() {
        stubHotelZone();
        stubRealizedBookings(threeAugustBookings());

        BigDecimal total = revenueService.getOtaCommissionTotal(FROM, TO);

        assertThat(total).isEqualByComparingTo("180000.00");
    }

    @Test
    void getDailyRevenueRejectsInvalidRange() {
        assertThatThrownBy(() -> revenueService.getDailyRevenue(TO, FROM))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void getRevenueByRoomTypeReturnsSnapshotRevenueAndAdrWithinLimit() {
        when(bookingRoomNightRepository.findRevenueByRoomType(eq(FROM), eq(TO), any(), any()))
                .thenReturn(List.of(
                        roomTypeRevenue("DELUXE", "Deluxe", new BigDecimal("3600000.00"), 3L),
                        roomTypeRevenue("STANDARD", "Standard", new BigDecimal("1600000.00"), 2L)
                ));

        List<RoomTypeRevenueBreakdown> result = revenueService.getRevenueByRoomType(FROM, TO, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roomTypeCode()).isEqualTo("DELUXE");
        assertThat(result.get(0).roomTypeName()).isEqualTo("Deluxe");
        assertThat(result.get(0).revenue()).isEqualByComparingTo("3600000.00");
        assertThat(result.get(0).roomNights()).isEqualTo(3L);
        assertThat(result.get(0).adr()).isEqualByComparingTo("1200000.00");
    }

    @Test
    void getRevenueByRoomTypeRejectsLimitOutsideAllowedRange() {
        assertThatThrownBy(() -> revenueService.getRevenueByRoomType(FROM, TO, 0))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> revenueService.getRevenueByRoomType(FROM, TO, 101))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("limit");
    }

    private void stubHotelZone() {
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY))
                .thenReturn("Asia/Ho_Chi_Minh");
    }

    private void stubRealizedBookings(List<BookingRevenueProjection> bookings) {
        when(bookingRepository.findRevenueRecognizedBookings(
                eq(BookingStatus.CHECKED_OUT), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(bookings);
    }

    /**
     * booking1: checked out 2026-08-01T10:00Z -> 2026-08-01T17:00 ICT (same day).
     * booking2: checked out 2026-08-01T18:00Z -> 2026-08-02T01:00 ICT (crosses midnight).
     * booking3: checked out 2026-08-02T02:00Z -> 2026-08-02T09:00 ICT (same day as booking2 locally).
     */
    private List<BookingRevenueProjection> threeAugustBookings() {
        return List.of(
                bookingRevenue(
                        OffsetDateTime.parse("2026-08-01T10:00:00Z"),
                        new BigDecimal("1000000.00"), new BigDecimal("800000.00"),
                        new BigDecimal("10.00"), "A", "Source A"
                ),
                bookingRevenue(
                        OffsetDateTime.parse("2026-08-01T18:00:00Z"),
                        new BigDecimal("2000000.00"), new BigDecimal("1500000.00"),
                        BigDecimal.ZERO, "B", "Source B"
                ),
                bookingRevenue(
                        OffsetDateTime.parse("2026-08-02T02:00:00Z"),
                        new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                        new BigDecimal("20.00"), "A", "Source A"
                )
        );
    }

    private NightRevenueProjection nightRevenue(BigDecimal roomRevenue, Long nightsCount) {
        return new NightRevenueProjection() {
            @Override
            public BigDecimal getRoomRevenue() {
                return roomRevenue;
            }

            @Override
            public Long getNightsCount() {
                return nightsCount;
            }
        };
    }

    private RoomTypeRevenueProjection roomTypeRevenue(
            String code,
            String name,
            BigDecimal revenue,
            Long roomNights
    ) {
        return new RoomTypeRevenueProjection() {
            @Override
            public String getRoomTypeCode() {
                return code;
            }

            @Override
            public String getRoomTypeName() {
                return name;
            }

            @Override
            public BigDecimal getRevenue() {
                return revenue;
            }

            @Override
            public Long getRoomNights() {
                return roomNights;
            }
        };
    }

    private BookingRevenueProjection bookingRevenue(
            OffsetDateTime checkedOutAt,
            BigDecimal totalAmount,
            BigDecimal roomsTotal,
            BigDecimal sourceCommissionPercentSnapshot,
            String sourceCode,
            String sourceName
    ) {
        return new BookingRevenueProjection() {
            @Override
            public OffsetDateTime getCheckedOutAt() {
                return checkedOutAt;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return totalAmount;
            }

            @Override
            public BigDecimal getRoomsTotal() {
                return roomsTotal;
            }

            @Override
            public BigDecimal getSourceCommissionPercentSnapshot() {
                return sourceCommissionPercentSnapshot;
            }

            @Override
            public String getSourceCode() {
                return sourceCode;
            }

            @Override
            public String getSourceName() {
                return sourceName;
            }
        };
    }
}
