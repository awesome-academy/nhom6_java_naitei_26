package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByBooking_Id(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "items"})
    Optional<Invoice> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"booking", "items"})
    Optional<Invoice> findFirstByBooking_PublicIdAndStatusOrderByCreatedAtDesc(
            String bookingPublicId,
            InvoiceStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT invoice FROM Invoice invoice WHERE invoice.publicId = :publicId")
    Optional<Invoice> findForUpdateByPublicId(@Param("publicId") String publicId);
}
