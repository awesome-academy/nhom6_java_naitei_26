package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.IdDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StaffBookingGuestCreateItem(
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^[A-Za-z]{2}$") String nationality,
        @NotNull IdDocumentType idDocumentType,
        @NotBlank @Size(max = 120) String idDocumentNumber,
        LocalDate dateOfBirth
) {
}
