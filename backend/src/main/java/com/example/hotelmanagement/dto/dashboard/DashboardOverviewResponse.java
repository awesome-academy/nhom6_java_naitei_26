package com.example.hotelmanagement.dto.dashboard;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardOverviewResponse(
        LocalDate date,
        BookingSummary bookingSummary,
        RoomSummary roomSummary,
        RevenueSummary revenueSummary,
        List<StayItem> arrivals,
        List<StayItem> departures,
        List<OccupancyDay> occupancyNext7Days
) {

    public record BookingSummary(
            long arrivalsCount,
            long departuresCount
    ) {
    }

    public record RoomSummary(
            long totalRooms,
            long availableRooms,
            long occupiedRooms,
            BigDecimal occupancyPercent
    ) {
    }

    public record RevenueSummary(
            BigDecimal currentMonthRevenue,
            BigDecimal previousMonthRevenue,
            BigDecimal changePercent,
            String currency
    ) {
    }

    public record StayItem(
            String bookingPublicId,
            String bookingCode,
            String contactName,
            String contactPhone,
            String roomNumber,
            String roomTypeName,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            BookingStatus bookingStatus,
            BookingRoomStatus bookingRoomStatus,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal balanceDue
    ) {
    }

    public record OccupancyDay(
            LocalDate date,
            long totalRooms,
            long availableRooms,
            long occupiedRooms,
            BigDecimal occupancyPercent
    ) {
    }
}
