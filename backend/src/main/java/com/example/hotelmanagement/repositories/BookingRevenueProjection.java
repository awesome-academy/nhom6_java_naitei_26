package com.example.hotelmanagement.repositories;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface BookingRevenueProjection {

    OffsetDateTime getCheckedOutAt();

    BigDecimal getTotalAmount();

    BigDecimal getRoomsTotal();

    BigDecimal getSourceCommissionPercentSnapshot();

    String getSourceCode();

    String getSourceName();
}
