package com.example.hotelmanagement.dto.payment;

import jakarta.validation.constraints.NotNull;

public record MockWalletResultRequest(
        @NotNull MockWalletResult result
) {
}
