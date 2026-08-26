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

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByBooking_Id(Long bookingId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @EntityGraph(attributePaths = {"booking", "items"})
    Optional<Invoice> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"booking", "items"})
    Optional<Invoice> findFirstByBooking_PublicIdAndStatusOrderByCreatedAtDesc(
            String bookingPublicId,
            InvoiceStatus status
    );

    @EntityGraph(attributePaths = {"booking", "items"})
    List<Invoice> findAllByBooking_PublicIdOrderByCreatedAtAscIdAsc(String bookingPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT invoice FROM Invoice invoice WHERE invoice.publicId = :publicId")
    Optional<Invoice> findForUpdateByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT invoice FROM Invoice invoice WHERE invoice.id = :invoiceId")
    Optional<Invoice> findForUpdateById(@Param("invoiceId") Long invoiceId);
}
