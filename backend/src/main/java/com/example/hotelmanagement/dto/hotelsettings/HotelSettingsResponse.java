package com.example.hotelmanagement.dto.hotelsettings;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalTime;

@Schema(name = "HotelSettingsResponse", description = "Hotel-wide operational settings")
public record HotelSettingsResponse(
        @JsonFormat(pattern = "HH:mm")
        @Schema(example = "14:00")
        LocalTime standardCheckInTime,

        @JsonFormat(pattern = "HH:mm")
        @Schema(example = "12:00")
        LocalTime defaultCheckoutTime,

        @Schema(description = "IANA timezone id", example = "Asia/Ho_Chi_Minh")
        String hotelTimezone,

        @Schema(description = "ISO 4217 currency code", example = "VND")
        String defaultCurrency,

        @Schema(example = "10.00")
        BigDecimal defaultRoomTaxPercent,

        @Schema(example = "30.00")
        BigDecimal defaultDepositPercent,

        @Schema(example = "100.00")
        BigDecimal defaultNoShowChargePercent
) {
}
