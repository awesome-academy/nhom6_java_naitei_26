package com.example.hotelmanagement.dto.cancellationpolicy;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record CancellationPolicyRuleSnapshot(
        @JsonProperty("min_hours_before") Integer minHoursBefore,
        @JsonProperty("refund_percent") BigDecimal refundPercent
) {
}
