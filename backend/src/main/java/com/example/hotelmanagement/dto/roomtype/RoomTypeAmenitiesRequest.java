package com.example.hotelmanagement.dto.roomtype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoomTypeAmenitiesRequest(
        @NotNull @Size(max = 100)
        Set<@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_]+$") String> amenityCodes
) {
}
