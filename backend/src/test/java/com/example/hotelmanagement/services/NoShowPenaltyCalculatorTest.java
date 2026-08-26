package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoShowPenaltyCalculatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoShowPenaltyCalculator calculator = new NoShowPenaltyCalculator(objectMapper);

    @Test
    void calculateUsesNoShowPercentFromEveryRoomPolicySnapshot() throws Exception {
        Booking booking = Booking.builder()
                .publicId("booking-public-id")
                .paidAmount(new BigDecimal("700.00"))
                .bookingRooms(Set.of(
                        room(11L, "2026-08-24", "1000.00", "FLEXIBLE", "50.00"),
                        room(12L, "2026-08-25", "500.00", "NON_REFUND", "20.00")
                ))
                .build();

        NoShowPenaltyCalculator.NoShowPenaltyCalculation calculation = calculator.calculate(booking);

        assertThat(calculation.penaltyAmount()).isEqualByComparingTo("600.00");
        assertThat(calculation.potentialRefundAmount()).isEqualByComparingTo("100.00");
        JsonNode metadata = objectMapper.readTree(calculation.metadataJson());
        assertThat(metadata.at("/rooms/0/no_show_charge_percent").decimalValue())
                .isEqualByComparingTo("50.00");
        assertThat(metadata.at("/penalty_amount").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(metadata.at("/potential_refund_amount").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void calculateRejectsSnapshotWithInvalidNoShowPercent() {
        Booking booking = Booking.builder()
                .publicId("booking-public-id")
                .bookingRooms(Set.of(room(11L, "2026-08-24", "1000.00", "FLEXIBLE", "101.00")))
                .build();

        assertThatThrownBy(() -> calculator.calculate(booking))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("invalid no-show charge percent");
    }

    private BookingRoom room(
            Long id,
            String checkInDate,
            String subtotal,
            String policyCode,
            String noShowChargePercent
    ) {
        BookingRoom room = BookingRoom.builder()
                .checkInDate(LocalDate.parse(checkInDate))
                .checkOutDate(LocalDate.parse(checkInDate).plusDays(1))
                .roomSubtotal(new BigDecimal(subtotal))
                .cancellationPolicySnapshot("""
                        {
                          "code": "%s",
                          "name": "%s",
                          "no_show_charge_percent": %s,
                          "price_adjustment_percent": 0,
                          "rules": []
                        }
                        """.formatted(policyCode, policyCode, noShowChargePercent))
                .build();
        room.setId(id);
        return room;
    }
}
