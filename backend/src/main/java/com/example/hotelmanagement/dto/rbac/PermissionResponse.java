package com.example.hotelmanagement.dto.rbac;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PermissionResponse", description = "RBAC permission data")
public record PermissionResponse(
    @Schema(description = "Permission code", example = "room:create")
    String code,

    @Schema(description = "Resource name", example = "room")
    String resource,

    @Schema(description = "Action name", example = "create")
    String action,

    @Schema(description = "Permission description", nullable = true)
    String description
) {
}
