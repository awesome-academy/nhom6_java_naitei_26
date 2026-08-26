package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "DELETE FROM PaymentEvent event WHERE event.payment.booking.id = :bookingId"
    )
    int deleteAllByBookingId(@org.springframework.data.repository.query.Param("bookingId") Long bookingId);
}
