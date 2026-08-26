package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingCheckInRequest;
import com.example.hotelmanagement.dto.booking.BookingCheckInRoomItem;
import com.example.hotelmanagement.dto.booking.StaffBookingGuestCreateItem;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestCreateRequest;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestIdentityDocumentResponse;
import com.example.hotelmanagement.dto.bookingguest.BookingGuestResponse;
import com.example.hotelmanagement.entity.AuditLog;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingGuest;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.AuditLogRepository;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Validated
@Transactional
public class BookingGuestService {

    private static final Logger log = LoggerFactory.getLogger(BookingGuestService.class);
    private static final int MAX_BOOKING_PUBLIC_ID_LENGTH = 36;
    private static final int MAX_FULL_NAME_LENGTH = 150;
    private static final int MAX_ID_DOCUMENT_NUMBER_LENGTH = 120;
    private static final Set<BookingStatus> GUEST_MUTABLE_BOOKING_STATUSES =
            Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final BookingGuestRepository bookingGuestRepository;
    private final BookingRepository bookingRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final AuditLogRepository auditLogRepository;
    private final GuestDocumentCryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BookingGuestService(
            BookingGuestRepository bookingGuestRepository,
            BookingRepository bookingRepository,
            BookingRoomRepository bookingRoomRepository,
            StaffProfileRepository staffProfileRepository,
            AuditLogRepository auditLogRepository,
            GuestDocumentCryptoService cryptoService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.bookingGuestRepository = bookingGuestRepository;
        this.bookingRepository = bookingRepository;
        this.bookingRoomRepository = bookingRoomRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.auditLogRepository = auditLogRepository;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.BOOKING_GUEST_MANAGE)
    @Transactional(readOnly = true)
    public List<BookingGuestResponse> getGuests(String bookingPublicId) {
        String normalizedBookingPublicId = normalizeBookingPublicId(bookingPublicId);
        if (!bookingRepository.existsByPublicId(normalizedBookingPublicId)) {
            throw new ResourceNotFoundException("Booking", normalizedBookingPublicId);
        }
        return bookingGuestRepository.findAllByBooking_PublicIdOrderByIdAsc(normalizedBookingPublicId)
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    @PreAuthorize(PermissionExpressions.BOOKING_GUEST_MANAGE)
    public BookingGuestResponse addGuest(
            String bookingPublicId,
            @Valid BookingGuestCreateRequest request,
            Long staffUserId
    ) {
        ensureStaffActor(staffUserId, "booking:assign_room");
        Booking booking = getGuestMutableBookingForUpdate(bookingPublicId);
        BookingRoom bookingRoom = resolveBookingRoom(booking.getPublicId(), request.bookingRoomId());
        validateDocumentPair(request);

        BookingGuest.BookingGuestBuilder guestBuilder = BookingGuest.builder()
                .booking(booking)
                .bookingRoom(bookingRoom)
                .fullName(normalizeRequiredText(request.fullName(), "Guest full name", MAX_FULL_NAME_LENGTH))
                .nationality(normalizeNationality(request.nationality()))
                .idDocumentType(request.idDocumentType())
                .dateOfBirth(request.dateOfBirth());

        String normalizedDocumentNumber = normalizeOptionalText(request.idDocumentNumber());
        if (normalizedDocumentNumber != null) {
            validateIdDocumentNumberLength(normalizedDocumentNumber);
            GuestDocumentCryptoService.EncryptedDocument encryptedDocument =
                    cryptoService.encrypt(normalizedDocumentNumber);
            guestBuilder
                    .idDocumentNumberEncrypted(encryptedDocument.ciphertext())
                    .idDocumentLookupHash(encryptedDocument.lookupHash());
        }

        return mapResponse(bookingGuestRepository.saveAndFlush(guestBuilder.build()));
    }

    /**
     * Replaces the temporary booking guests with the guests verified at the front desk.
     * The operation is intentionally kept in the same transaction as check-in so a failed
     * state transition cannot leave a partially updated guest list.
     */
    @PreAuthorize(PermissionExpressions.BOOKING_CHECK_IN)
    public void replaceGuestsForCheckIn(
            String bookingPublicId,
            @Valid BookingCheckInRequest request,
            Long staffUserId
    ) {
        ensureStaffActor(staffUserId, "booking:check_in");
        Booking booking = bookingRepository.findForUpdateByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessValidationException("Only confirmed bookings can capture check-in guests");
        }
        if (request == null || request.rooms() == null || request.rooms().isEmpty()) {
            throw new BusinessValidationException("Guest information is required for check-in");
        }

        Map<Long, BookingRoom> bookingRoomsById = booking.getBookingRooms().stream()
                .collect(java.util.stream.Collectors.toMap(BookingRoom::getId, room -> room));
        Set<Long> requestedRoomIds = new java.util.HashSet<>();
        int totalGuests = 0;
        List<BookingGuest> replacementGuests = new java.util.ArrayList<>();

        for (BookingCheckInRoomItem roomItem : request.rooms()) {
            if (roomItem == null || roomItem.bookingRoomId() == null) {
                throw new BusinessValidationException("Booking room is required for check-in");
            }
            if (!requestedRoomIds.add(roomItem.bookingRoomId())) {
                throw new BusinessValidationException("A booking room can only appear once in check-in data");
            }
            BookingRoom bookingRoom = bookingRoomsById.get(roomItem.bookingRoomId());
            if (bookingRoom == null) {
                throw new ResourceNotFoundException("Booking room", roomItem.bookingRoomId().toString());
            }
            if (roomItem.guestCount() == null || roomItem.guestCount() < 1
                    || roomItem.guests() == null || roomItem.guests().size() != roomItem.guestCount()) {
                throw new BusinessValidationException("Guest list must match the guest count for the room");
            }

            bookingRoom.setGuestCount(roomItem.guestCount());
            totalGuests += roomItem.guestCount();
            for (StaffBookingGuestCreateItem guestItem : roomItem.guests()) {
                replacementGuests.add(buildCheckInGuest(booking, bookingRoom, guestItem));
            }
        }

        if (requestedRoomIds.size() != bookingRoomsById.size()) {
            throw new BusinessValidationException("Check-in data must include every booking room");
        }

        bookingGuestRepository.deleteAllByBookingId(booking.getId());
        booking.getBookingGuests().clear();
        booking.getBookingGuests().addAll(replacementGuests);
        booking.setAdults(totalGuests);
        booking.setChildren(0);
        bookingGuestRepository.saveAllAndFlush(replacementGuests);
    }

    private BookingGuest buildCheckInGuest(
            Booking booking,
            BookingRoom bookingRoom,
            StaffBookingGuestCreateItem guestItem
    ) {
        if (guestItem == null) {
            throw new BusinessValidationException("Guest information is required");
        }
        String fullName = normalizeRequiredText(guestItem.fullName(), "Guest full name", MAX_FULL_NAME_LENGTH);
        String documentNumber = normalizeRequiredText(
                guestItem.idDocumentNumber(),
                "Guest identity document number",
                MAX_ID_DOCUMENT_NUMBER_LENGTH
        );
        if (guestItem.idDocumentType() == null) {
            throw new BusinessValidationException("Guest identity document type is required");
        }
        GuestDocumentCryptoService.EncryptedDocument encryptedDocument = cryptoService.encrypt(documentNumber);
        return BookingGuest.builder()
                .booking(booking)
                .bookingRoom(bookingRoom)
                .fullName(fullName)
                .nationality(normalizeNationality(guestItem.nationality()))
                .idDocumentType(guestItem.idDocumentType())
                .idDocumentNumberEncrypted(encryptedDocument.ciphertext())
                .idDocumentLookupHash(encryptedDocument.lookupHash())
                .dateOfBirth(guestItem.dateOfBirth())
                .build();
    }

    @PreAuthorize(PermissionExpressions.GUEST_READ_ID)
    public BookingGuestIdentityDocumentResponse revealIdentityDocument(
            String bookingPublicId,
            Long guestId,
            Long actorUserId,
            String ipAddress,
            String userAgent
    ) {
        String normalizedBookingPublicId = normalizeBookingPublicId(bookingPublicId);
        Long normalizedGuestId = validatePositiveId(guestId, "Booking guest id");
        BookingGuest guest = bookingGuestRepository
                .findByIdAndBooking_PublicId(normalizedGuestId, normalizedBookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking guest", normalizedGuestId.toString()));

        if (guest.getIdDocumentNumberEncrypted() == null) {
            throw new BusinessValidationException("Guest identity document is not available");
        }

        String documentNumber = cryptoService.decrypt(guest.getIdDocumentNumberEncrypted());
        OffsetDateTime accessedAt = OffsetDateTime.now(clock);
        auditLogRepository.save(AuditLog.builder()
                .actorUserId(validatePositiveId(actorUserId, "Actor user id"))
                .action("GUEST_ID_DOCUMENT_READ")
                .entityType("booking_guest")
                .entityId(guest.getId())
                .beforeData(null)
                .afterData(buildIdentityDocumentReadAuditData(guest, normalizedBookingPublicId))
                .ipAddress(normalizeOptionalText(ipAddress))
                .userAgent(normalizeOptionalText(userAgent))
                .build());

        return new BookingGuestIdentityDocumentResponse(
                guest.getId(),
                normalizedBookingPublicId,
                guest.getIdDocumentType(),
                documentNumber,
                accessedAt
        );
    }

    private Booking getGuestMutableBookingForUpdate(String bookingPublicId) {
        String normalizedBookingPublicId = normalizeBookingPublicId(bookingPublicId);
        Booking booking = bookingRepository.findForUpdateByPublicId(normalizedBookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", normalizedBookingPublicId));
        if (!GUEST_MUTABLE_BOOKING_STATUSES.contains(booking.getStatus())) {
            throw new BusinessValidationException("Guests can only be changed before checkout or cancellation");
        }
        return booking;
    }

    private BookingRoom resolveBookingRoom(String bookingPublicId, Long bookingRoomId) {
        if (bookingRoomId == null) {
            return null;
        }
        Long normalizedBookingRoomId = validatePositiveId(bookingRoomId, "Booking room id");
        return bookingRoomRepository
                .findByIdAndBookingPublicId(normalizedBookingRoomId, bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking room",
                        normalizedBookingRoomId.toString()
                ));
    }

    private void validateDocumentPair(BookingGuestCreateRequest request) {
        boolean hasDocumentType = request.idDocumentType() != null;
        boolean hasDocumentNumber = normalizeOptionalText(request.idDocumentNumber()) != null;
        if (hasDocumentType != hasDocumentNumber) {
            throw new BusinessValidationException(
                    "Guest identity document type and number must be provided together"
            );
        }
    }

    private void ensureStaffActor(Long staffUserId, String requiredAuthority) {
        Long normalizedStaffUserId = validatePositiveId(staffUserId, "Staff user id");
        StaffProfile staff = staffProfileRepository.findByUser_Id(normalizedStaffUserId)
                .orElse(null);
        if (staff != null && staff.getId() != null) {
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null
                || SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .noneMatch(authority -> requiredAuthority.equals(authority.getAuthority()))) {
            throw new BusinessValidationException("Only staff can manage booking guests");
        }
    }

    private String buildIdentityDocumentReadAuditData(
            BookingGuest guest,
            String bookingPublicId
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bookingPublicId", bookingPublicId,
                    "guestId", guest.getId(),
                    "idDocumentType", guest.getIdDocumentType() == null
                            ? "UNKNOWN"
                            : guest.getIdDocumentType().name()
            ));
        } catch (JsonProcessingException exception) {
            log.error(
                    "Failed to serialize guest identity document audit data bookingPublicId={} guestId={}",
                    bookingPublicId,
                    guest.getId(),
                    exception
            );
            throw new IllegalStateException("Failed to serialize audit data", exception);
        }
    }

    private String normalizeBookingPublicId(String publicId) {
        return normalizeRequiredText(publicId, "Booking public id", MAX_BOOKING_PUBLIC_ID_LENGTH);
    }

    private String normalizeNationality(String nationality) {
        String normalized = normalizeOptionalText(nationality);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private void validateIdDocumentNumberLength(String documentNumber) {
        if (documentNumber.length() > MAX_ID_DOCUMENT_NUMBER_LENGTH) {
            throw new BusinessValidationException(
                    "Guest identity document number must not exceed "
                            + MAX_ID_DOCUMENT_NUMBER_LENGTH
                            + " characters"
            );
        }
    }

    private Long validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException(fieldName + " must be a positive number");
        }
        return id;
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessValidationException(
                    fieldName + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private BookingGuestResponse mapResponse(BookingGuest guest) {
        BookingRoom bookingRoom = guest.getBookingRoom();
        return new BookingGuestResponse(
                guest.getId(),
                guest.getBooking().getPublicId(),
                bookingRoom == null ? null : bookingRoom.getId(),
                bookingRoom == null ? null : bookingRoom.getRoom().getRoomNumber(),
                guest.getFullName(),
                guest.getNationality(),
                guest.getIdDocumentType(),
                guest.getIdDocumentNumberEncrypted() != null,
                guest.getDateOfBirth(),
                guest.getCreatedAt()
        );
    }
}
