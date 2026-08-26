package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "StaffPasswordUpdateRequest", description = "Admin-assigned Staff password")
public record StaffPasswordUpdateRequest(
        @NotBlank
        @Size(min = 12, max = 64)
        String newPassword
) {
}
