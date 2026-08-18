package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.IdDocumentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "booking_guests",
        indexes = {
                @Index(name = "idx_bg_booking", columnList = "booking_id"),
                @Index(name = "idx_bg_booking_room", columnList = "booking_room_id"),
                @Index(name = "idx_bg_doc_hash", columnList = "id_document_lookup_hash")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGuest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_room_id")
    private BookingRoom bookingRoom;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 2)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_document_type")
    private IdDocumentType idDocumentType;

    @Column(name = "id_document_number_encrypted", columnDefinition = "VARBINARY(512)")
    private byte[] idDocumentNumberEncrypted;

    @Column(name = "id_document_lookup_hash", columnDefinition = "VARBINARY(64)")
    private byte[] idDocumentLookupHash;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
}
