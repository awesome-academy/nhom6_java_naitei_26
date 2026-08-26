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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final LocalDate DASHBOARD_DATE = LocalDate.of(2026, 8, 25);

    private BookingRoomRepository bookingRoomRepository;
    private RoomRepository roomRepository;
    private RevenueService revenueService;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        bookingRoomRepository = mock(BookingRoomRepository.class);
        roomRepository = mock(RoomRepository.class);
        revenueService = mock(RevenueService.class);
        HotelSettingsRepository hotelSettingsRepository = mock(HotelSettingsRepository.class);

        dashboardService = new DashboardService(
                bookingRoomRepository,
                roomRepository,
                revenueService,
                hotelSettingsRepository,
                java.time.Clock.systemUTC()
        );
    }

    @Test
    void buildsOverviewFromArrivalsOccupancyAndMonthlyRevenue() {
        DashboardStayProjection arrival = mock(DashboardStayProjection.class);
        when(arrival.getBookingPublicId()).thenReturn("booking-public-id");
        when(arrival.getBookingCode()).thenReturn("BK-100");
        when(arrival.getContactName()).thenReturn("Nguyen Van A");
        when(arrival.getRoomNumber()).thenReturn("301");
        when(arrival.getRoomTypeName()).thenReturn("Deluxe");
        when(arrival.getCheckInDate()).thenReturn(DASHBOARD_DATE);
        when(arrival.getCheckOutDate()).thenReturn(DASHBOARD_DATE.plusDays(2));
        when(arrival.getBookingStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(arrival.getBookingRoomStatus()).thenReturn(BookingRoomStatus.RESERVED);
        when(arrival.getTotalAmount()).thenReturn(new BigDecimal("3000000"));
        when(arrival.getPaidAmount()).thenReturn(new BigDecimal("1000000"));
        when(arrival.getRefundedAmount()).thenReturn(BigDecimal.ZERO);

        when(bookingRoomRepository.findDashboardArrivals(DASHBOARD_DATE)).thenReturn(List.of(arrival));
        when(bookingRoomRepository.findDashboardDepartures(DASHBOARD_DATE)).thenReturn(List.of());
        when(roomRepository.countActiveOperationalRooms(RoomOperationalStatus.ACTIVE)).thenReturn(10L);
        when(roomRepository.countAvailableOnDate(
                any(), any(), eq(RoomOperationalStatus.ACTIVE), anySet()
        )).thenReturn(7L, 6L, 6L, 5L, 5L, 8L, 8L);
        when(roomRepository.countOccupiedOrReservedOnDate(any(), any(), anySet(), anySet()))
                .thenReturn(3L, 4L, 4L, 5L, 5L, 2L, 2L);
        when(revenueService.getMonthlyRevenue(
                YearMonth.of(2026, 8).atDay(1),
                YearMonth.of(2026, 8).atEndOfMonth()
        )).thenReturn(List.of(new MonthlyRevenuePoint(
                YearMonth.of(2026, 8),
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                1
        )));
        when(revenueService.getMonthlyRevenue(
                YearMonth.of(2026, 7).atDay(1),
                YearMonth.of(2026, 7).atEndOfMonth()
        )).thenReturn(List.of(new MonthlyRevenuePoint(
                YearMonth.of(2026, 7),
                new BigDecimal("500000"),
                BigDecimal.ZERO,
                1
        )));

        DashboardOverviewResponse response = dashboardService.getOverview(DASHBOARD_DATE);

        assertEquals(1, response.bookingSummary().arrivalsCount());
        assertEquals(0, response.bookingSummary().departuresCount());
        assertEquals(7, response.roomSummary().availableRooms());
        assertEquals(3, response.roomSummary().occupiedRooms());
        assertEquals(new BigDecimal("30.00"), response.roomSummary().occupancyPercent());
        assertEquals(new BigDecimal("1000000.00"), response.revenueSummary().currentMonthRevenue());
        assertEquals(new BigDecimal("100.00"), response.revenueSummary().changePercent());
        assertEquals(new BigDecimal("2000000.00"), response.arrivals().get(0).balanceDue());
        assertEquals(7, response.occupancyNext7Days().size());
    }
}
