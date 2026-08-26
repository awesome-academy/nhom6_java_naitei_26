package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.EmailMessage;
import com.example.hotelmanagement.entity.enums.EmailStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT message
            FROM EmailMessage message
            WHERE message.status = :status
              AND (message.scheduledAt IS NULL OR message.scheduledAt <= :now)
            ORDER BY message.createdAt, message.id
            """)
    List<EmailMessage> findDueForUpdate(
            @Param("status") EmailStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE EmailMessage message
            SET message.status = :queuedStatus,
                message.scheduledAt = :now,
                message.lastError = :reason,
                message.updatedAt = :now
            WHERE message.status = :sendingStatus
              AND message.updatedAt < :staleBefore
            """)
    int recoverStaleMessages(
            @Param("sendingStatus") EmailStatus sendingStatus,
            @Param("queuedStatus") EmailStatus queuedStatus,
            @Param("staleBefore") OffsetDateTime staleBefore,
            @Param("now") OffsetDateTime now,
            @Param("reason") String reason
    );

    List<EmailMessage> findTop20ByRelatedBookingIdOrderByCreatedAtDesc(Long relatedBookingId);
}
