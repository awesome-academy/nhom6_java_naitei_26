package com.example.hotelmanagement.dto.rbac;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "RolePermissionUpdateRequest", description = "Role permission replacement payload")
public record RolePermissionUpdateRequest(
    @Schema(description = "Permission codes assigned to the role", example = "[\"room:read\",\"room:create\"]")
    @NotNull
    @Size(max = 100)
    Set<@NotNull @Size(max = 60) @Pattern(regexp = "^[a-z0-9_]+:[a-z0-9_]+$") String> permissionCodes
) {
}
