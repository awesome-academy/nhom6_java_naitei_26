package com.example.hotelmanagement.repositories;

import java.math.BigDecimal;

public interface RoomTypeRevenueProjection {

    String getRoomTypeCode();

    String getRoomTypeName();

    BigDecimal getRevenue();

    Long getRoomNights();
}
