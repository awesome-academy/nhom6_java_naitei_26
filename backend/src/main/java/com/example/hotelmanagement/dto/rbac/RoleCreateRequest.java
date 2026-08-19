package com.example.hotelmanagement.dto.rbac;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RoleCreateRequest", description = "Custom role creation payload")
public record RoleCreateRequest(
    @Schema(description = "Unique role code", example = "MANAGER")
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Za-z0-9_]+$")
    String code,

    @Schema(description = "Role display name", example = "Manager")
    @NotBlank
    @Size(max = 80)
    String name,

    @Schema(description = "Role description", nullable = true)
    @Size(max = 1000)
    String description
) {
}
