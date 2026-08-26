package com.example.hotelmanagement.dto.hotelsettings;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

@Schema(name = "HotelSettingsUpdateRequest", description = "Partial update payload for hotel-wide operational settings. Omitted fields are left unchanged.")
public record HotelSettingsUpdateRequest(
        @JsonFormat(pattern = "HH:mm")
        @Schema(example = "14:00", nullable = true)
        LocalTime standardCheckInTime,

        @JsonFormat(pattern = "HH:mm")
        @Schema(example = "12:00", nullable = true)
        LocalTime defaultCheckoutTime,

        @Schema(description = "IANA timezone id", example = "Asia/Ho_Chi_Minh", nullable = true)
        @Size(min = 2, max = 50)
        String hotelTimezone,

        @Schema(description = "ISO 4217 currency code", example = "VND", nullable = true)
        @Size(min = 3, max = 3)
        String defaultCurrency,

        @Schema(example = "10.00", nullable = true)
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal defaultRoomTaxPercent,

        @Schema(example = "100.00", nullable = true)
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal defaultNoShowChargePercent
) {
}
