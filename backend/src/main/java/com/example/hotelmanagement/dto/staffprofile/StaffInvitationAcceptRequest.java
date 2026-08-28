package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "StaffInvitationAcceptRequest", description = "Activate a Staff invitation")
public record StaffInvitationAcceptRequest(
        @NotBlank
        @Size(min = 32, max = 256)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String token
) {

    /** @deprecated Password replacement is no longer part of invitation acceptance. */
    @Deprecated
    public StaffInvitationAcceptRequest(String token, String ignoredNewPassword) {
        this(token);
    }
}
