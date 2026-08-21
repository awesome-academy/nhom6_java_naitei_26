package com.example.hotelmanagement.dto.bookingguest;

import com.example.hotelmanagement.entity.enums.IdDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BookingGuestCreateRequest(
        Long bookingRoomId,

        @NotBlank
        @Size(max = 150)
        String fullName,

        @Pattern(regexp = "^[A-Za-z]{2}$")
        String nationality,

        IdDocumentType idDocumentType,

        @Size(max = 120)
        String idDocumentNumber,

        LocalDate dateOfBirth
) {
}
