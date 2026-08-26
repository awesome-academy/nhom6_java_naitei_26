package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "StaffInvitationAcceptRequest", description = "Accept Staff invitation and set the permanent password")
public record StaffInvitationAcceptRequest(
        @NotBlank
        @Size(min = 32, max = 256)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String token,

        @NotBlank
        @Size(min = 12, max = 64)
        String newPassword
) {
}
