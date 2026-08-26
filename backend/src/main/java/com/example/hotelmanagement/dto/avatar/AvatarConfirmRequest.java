package com.example.hotelmanagement.dto.avatar;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AvatarConfirmRequest(@NotNull UUID uploadId) {
}
