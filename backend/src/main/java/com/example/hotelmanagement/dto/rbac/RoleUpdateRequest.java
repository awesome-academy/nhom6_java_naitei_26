package com.example.hotelmanagement.dto.rbac;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "RoleUpdateRequest", description = "Role update payload")
public record RoleUpdateRequest(
    @Schema(description = "Role display name", example = "Front Desk Staff", nullable = true)
    @Size(max = 80)
    String name,

    @Schema(description = "Role description", nullable = true)
    @Size(max = 1000)
    String description
) {
}
