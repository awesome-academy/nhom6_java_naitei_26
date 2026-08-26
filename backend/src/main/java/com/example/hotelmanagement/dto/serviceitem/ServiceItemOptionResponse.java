package com.example.hotelmanagement.dto.serviceitem;

import com.example.hotelmanagement.entity.enums.ServiceCategory;

import java.math.BigDecimal;

public record ServiceItemOptionResponse(
        String code,
        String name,
        ServiceCategory category,
        BigDecimal unitPrice,
        BigDecimal taxPercent
) {
}
