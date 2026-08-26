package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "StaffInvitationResendRequest", description = "Replace the temporary password and resend a Staff invitation")
public record StaffInvitationResendRequest(
        @NotBlank
        @Size(min = 12, max = 64)
        String temporaryPassword
) {
}
