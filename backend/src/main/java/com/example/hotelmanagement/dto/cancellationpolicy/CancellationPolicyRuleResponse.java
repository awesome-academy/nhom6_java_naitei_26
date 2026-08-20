package com.example.hotelmanagement.dto.cancellationpolicy;

import java.math.BigDecimal;

public record CancellationPolicyRuleResponse(
        Integer minHoursBefore,
        BigDecimal refundPercent
) {
}
