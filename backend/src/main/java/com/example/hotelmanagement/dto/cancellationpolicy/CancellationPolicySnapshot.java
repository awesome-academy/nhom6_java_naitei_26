package com.example.hotelmanagement.dto.cancellationpolicy;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record CancellationPolicySnapshot(
        String code,
        String name,
        @JsonProperty("no_show_charge_percent") BigDecimal noShowChargePercent,
        @JsonProperty("price_adjustment_percent") BigDecimal priceAdjustmentPercent,
        List<CancellationPolicyRuleSnapshot> rules
) {
}
