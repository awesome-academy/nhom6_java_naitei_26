package com.example.hotelmanagement.dto.rbac;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "RoleResponse", description = "RBAC role data")
public record RoleResponse(
    @Schema(description = "Role code", example = "STAFF")
    String code,

    @Schema(description = "Role display name", example = "Staff")
    String name,

    @Schema(description = "Role description", nullable = true)
    String description,

    @Schema(description = "System roles cannot be deleted")
    Boolean isSystem,

    @Schema(description = "Permissions assigned to the role")
    List<PermissionResponse> permissions,

    @Schema(description = "Created timestamp")
    OffsetDateTime createdAt,

    @Schema(description = "Updated timestamp")
    OffsetDateTime updatedAt
) {
}
