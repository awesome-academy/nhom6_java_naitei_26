package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "StaffOwnProfileUpdateRequest", description = "Self-service update payload for a staff profile.")
public record StaffOwnProfileUpdateRequest(
        @Schema(description = "Phone number. A blank value clears the phone.")
        @NotNull
        @Size(max = 20)
        @Pattern(regexp = "^[0-9+() .-]*$")
        String phone
) {
}
