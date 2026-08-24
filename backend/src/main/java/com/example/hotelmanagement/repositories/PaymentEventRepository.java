package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
