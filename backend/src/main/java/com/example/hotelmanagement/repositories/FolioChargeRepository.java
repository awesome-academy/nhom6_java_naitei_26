package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.FolioCharge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolioChargeRepository extends JpaRepository<FolioCharge, Long> {

    @EntityGraph(attributePaths = {"booking", "serviceItem"})
    List<FolioCharge> findAllByBooking_PublicIdOrderByChargedAtAscIdAsc(String bookingPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"booking", "serviceItem"})
    @Query("""
            SELECT charge
            FROM FolioCharge charge
            WHERE charge.id = :chargeId
              AND charge.booking.id = :bookingId
            """)
    Optional<FolioCharge> findForUpdateByIdAndBookingId(
            @Param("chargeId") Long chargeId,
            @Param("bookingId") Long bookingId
    );
}
