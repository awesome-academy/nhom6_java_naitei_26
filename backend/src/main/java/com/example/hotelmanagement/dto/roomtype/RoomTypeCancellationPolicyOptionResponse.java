package com.example.hotelmanagement.dto.roomtype;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;

public record RoomTypeCancellationPolicyOptionResponse(
        CancellationPolicyResponse cancellationPolicy,
        Boolean isActive,
        Integer sortOrder
) {
}
