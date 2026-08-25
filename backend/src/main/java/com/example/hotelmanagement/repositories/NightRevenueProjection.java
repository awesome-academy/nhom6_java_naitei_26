package com.example.hotelmanagement.repositories;

import java.math.BigDecimal;

public interface NightRevenueProjection {

    BigDecimal getRoomRevenue();

    Long getNightsCount();
}
