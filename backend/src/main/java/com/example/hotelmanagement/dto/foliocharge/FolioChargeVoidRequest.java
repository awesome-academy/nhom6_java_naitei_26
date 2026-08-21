package com.example.hotelmanagement.dto.foliocharge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FolioChargeVoidRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
