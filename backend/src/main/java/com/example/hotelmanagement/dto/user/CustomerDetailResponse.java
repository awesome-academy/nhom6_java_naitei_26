package com.example.hotelmanagement.dto.user;

import com.example.hotelmanagement.dto.customerprofile.CustomerProfileResponse;

public record CustomerDetailResponse(
        UserResponse account,
        CustomerProfileResponse profile
) {
}
