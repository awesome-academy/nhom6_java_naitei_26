package com.example.hotelmanagement.dto.roomstatusblock;

import com.example.hotelmanagement.entity.enums.RoomBlockType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RoomStatusBlockCreateRequest(
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String roomNumber,

        @NotNull RoomBlockType blockType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,

        @Size(max = 10_000)
        String reason
) {
}
