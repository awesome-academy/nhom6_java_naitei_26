package com.example.hotelmanagement.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReplyRequest(
        @NotBlank @Size(max = 10000) String staffReply
) {
}
