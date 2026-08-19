package com.example.hotelmanagement.dto.user;

import com.example.hotelmanagement.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UserUpdateRequest", description = "Partial user account update payload")
public record UserUpdateRequest(
    @Schema(description = "User full name", example = "Nguyen Van A", nullable = true)
    @Size(max = 150)
    String fullName,

    @Schema(description = "Optional phone number. Blank value clears the phone.", example = "+84901234567", nullable = true)
    @Size(max = 20)
    @Pattern(regexp = "^[0-9+() .-]*$")
    String phone,

    @Schema(description = "Optional avatar URL. Blank value clears the avatar.", nullable = true)
    @Size(max = 2048)
    String avatarUrl,

    @Schema(description = "User account status", example = "ACTIVE", nullable = true)
    UserStatus status
) {
}
