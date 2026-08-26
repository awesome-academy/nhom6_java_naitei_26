package com.example.hotelmanagement.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "StaffBookingCreateRequest", description = "Create a held booking for a selected physical room")
public record StaffBookingCreateRequest(
        @NotBlank @Size(max = 150) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[0-9+() .-]*$") String contactPhone,
        @Size(max = 2000) String specialRequests,
        @NotEmpty @Valid List<StaffBookingRoomCreateItem> rooms
) {
}
