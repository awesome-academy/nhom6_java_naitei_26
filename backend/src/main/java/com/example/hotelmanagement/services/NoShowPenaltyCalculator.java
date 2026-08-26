package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicySnapshot;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Calculates no-show charges from the immutable policy snapshot on each booking room. */
@Service
public class NoShowPenaltyCalculator {

    private static final Logger log = LoggerFactory.getLogger(NoShowPenaltyCalculator.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    private final ObjectMapper objectMapper;

    public NoShowPenaltyCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NoShowPenaltyCalculation calculate(Booking booking) {
        if (booking == null || booking.getBookingRooms() == null || booking.getBookingRooms().isEmpty()) {
            throw new BusinessValidationException("Booking has no rooms to calculate a no-show penalty for");
        }

        List<BookingRoom> rooms = booking.getBookingRooms().stream()
                .sorted(Comparator.comparing(BookingRoom::getCheckInDate))
                .toList();
        List<NoShowRoomBreakdown> breakdowns = new ArrayList<>();
        BigDecimal penalty = BigDecimal.ZERO;
        for (BookingRoom room : rooms) {
            CancellationPolicySnapshot snapshot = readSnapshot(booking, room);
            BigDecimal chargePercent = validateChargePercent(booking, room, snapshot.noShowChargePercent());
            BigDecimal roomSubtotal = requireRoomSubtotal(booking, room);
            BigDecimal roomPenalty = normalize(roomSubtotal.multiply(chargePercent).divide(HUNDRED));
            penalty = penalty.add(roomPenalty);
            breakdowns.add(new NoShowRoomBreakdown(
                    room.getId(),
                    room.getCheckInDate().toString(),
                    snapshot.code(),
                    chargePercent,
                    roomSubtotal,
                    roomPenalty
            ));
        }

        BigDecimal normalizedPenalty = normalize(penalty);
        BigDecimal paidAmount = normalize(booking.getPaidAmount());
        BigDecimal potentialRefund = normalize(paidAmount.subtract(normalizedPenalty).max(BigDecimal.ZERO));
        NoShowPenaltySnapshot result = new NoShowPenaltySnapshot(
                breakdowns,
                paidAmount,
                normalizedPenalty,
                potentialRefund
        );
        return new NoShowPenaltyCalculation(
                normalizedPenalty,
                potentialRefund,
                writeSnapshot(booking, result)
        );
    }

    private CancellationPolicySnapshot readSnapshot(Booking booking, BookingRoom room) {
        if (room.getCancellationPolicySnapshot() == null || room.getCancellationPolicySnapshot().isBlank()) {
            throw new BusinessValidationException(
                    "Booking room " + roomIdentifier(room) + " has no cancellation policy snapshot"
            );
        }
        try {
            return objectMapper.readValue(room.getCancellationPolicySnapshot(), CancellationPolicySnapshot.class);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Cannot read no-show policy snapshot bookingPublicId={} bookingRoomId={}",
                    booking.getPublicId(),
                    room.getId(),
                    exception
            );
            throw new BusinessValidationException(
                    "Booking room " + roomIdentifier(room) + " has an unreadable cancellation policy snapshot"
            );
        }
    }

    private BigDecimal validateChargePercent(Booking booking, BookingRoom room, BigDecimal chargePercent) {
        if (chargePercent == null
                || chargePercent.compareTo(BigDecimal.ZERO) < 0
                || chargePercent.compareTo(HUNDRED) > 0) {
            throw new BusinessValidationException(
                    "Booking room " + roomIdentifier(room) + " has an invalid no-show charge percent"
            );
        }
        return chargePercent;
    }

    private BigDecimal requireRoomSubtotal(Booking booking, BookingRoom room) {
        if (room.getRoomSubtotal() == null || room.getRoomSubtotal().signum() < 0) {
            throw new BusinessValidationException(
                    "Booking room " + roomIdentifier(room) + " has an invalid room subtotal"
            );
        }
        return normalize(room.getRoomSubtotal());
    }

    private String writeSnapshot(Booking booking, NoShowPenaltySnapshot snapshot) {
        try {
            return objectMapper.writer()
                    .with(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            log.error(
                    "Cannot write no-show penalty snapshot bookingPublicId={}",
                    booking.getPublicId(),
                    exception
            );
            throw new BusinessValidationException("Unable to save the no-show penalty calculation");
        }
    }

    private BigDecimal normalize(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String roomIdentifier(BookingRoom room) {
        return room.getId() == null ? "unknown" : room.getId().toString();
    }

    public record NoShowPenaltyCalculation(
            BigDecimal penaltyAmount,
            BigDecimal potentialRefundAmount,
            String metadataJson
    ) {
    }

    private record NoShowRoomBreakdown(
            @JsonProperty("booking_room_id") Long bookingRoomId,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("cancellation_policy_code") String cancellationPolicyCode,
            @JsonProperty("no_show_charge_percent") BigDecimal noShowChargePercent,
            @JsonProperty("room_subtotal") BigDecimal roomSubtotal,
            @JsonProperty("penalty_amount") BigDecimal penaltyAmount
    ) {
    }

    private record NoShowPenaltySnapshot(
            @JsonProperty("rooms") List<NoShowRoomBreakdown> rooms,
            @JsonProperty("paid_amount") BigDecimal paidAmount,
            @JsonProperty("penalty_amount") BigDecimal penaltyAmount,
            @JsonProperty("potential_refund_amount") BigDecimal potentialRefundAmount
    ) {
    }
}
