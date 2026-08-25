package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.*;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceItemResponse;
import com.example.hotelmanagement.dto.payment.PaymentResponse;
import com.example.hotelmanagement.dto.room.AvailableRoomForAssignmentResponse;
import com.example.hotelmanagement.entity.*;
import com.example.hotelmanagement.entity.enums.*;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookingStaffService {

    private final BookingRepository bookingRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final RoomRepository roomRepository;
    private final FolioChargeRepository folioChargeRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BookingGuestRepository bookingGuestRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final UserRepository userRepository;
    private final BookingStateMachineService bookingStateMachineService;
    private final Clock clock;

    public BookingStaffService(
            BookingRepository bookingRepository,
            BookingRoomRepository bookingRoomRepository,
            RoomRepository roomRepository,
            FolioChargeRepository folioChargeRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            BookingGuestRepository bookingGuestRepository,
            StaffProfileRepository staffProfileRepository,
            UserRepository userRepository,
            BookingStateMachineService bookingStateMachineService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingRoomRepository = bookingRoomRepository;
        this.roomRepository = roomRepository;
        this.folioChargeRepository = folioChargeRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.bookingGuestRepository = bookingGuestRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.userRepository = userRepository;
        this.bookingStateMachineService = bookingStateMachineService;
        this.clock = clock;
    }

    /**
     * Get paginated list of bookings with filters (Staff view).
     */
    public BookingListResponse getAllBookings(BookingListFilterRequest filter) {
        Pageable pageable = PageRequest.of(
                filter.page(),
                filter.size(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Booking> bookingPage;

        // Apply filters based on provided parameters
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            bookingPage = bookingRepository.findAllByStatusIn(filter.statuses(), pageable);
        } else if (filter.sourceCode() != null && !filter.sourceCode().isBlank()) {
            bookingPage = bookingRepository.findAllBySourceCode(filter.sourceCode().toUpperCase(), pageable);
        } else {
            bookingPage = bookingRepository.findAllOrderByCreatedAtDesc(pageable);
        }

        List<BookingListItemResponse> items = bookingPage.getContent().stream()
                .map(this::mapToListItem)
                .toList();

        // Get stats
        BookingListResponse.BookingStats stats = getBookingStats();

        return new BookingListResponse(
                items,
                bookingPage.getNumber(),
                bookingPage.getSize(),
                (int) bookingPage.getTotalElements(),
                bookingPage.getTotalPages(),
                stats
        );
    }

    /**
     * Get full booking detail for Staff view.
     */
    public BookingStaffDetailResponse getStaffBookingDetail(String publicId) {
        Booking booking = bookingRepository.findForStaffDetailByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", publicId));

        return mapToStaffDetailResponse(booking);
    }

    /**
     * Get available rooms for assignment to a booking room.
     */
    public List<AvailableRoomForAssignmentResponse> getAvailableRoomsForAssignment(
            String bookingPublicId,
            Long bookingRoomId,
            Integer floor,
            HousekeepingStatus housekeepingStatus,
            RoomView viewType
    ) {
        // Get booking room to find dates and room type
        Booking booking = bookingRepository.findForStaffDetailByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));

        BookingRoom bookingRoom = booking.getBookingRooms().stream()
                .filter(br -> br.getId().equals(bookingRoomId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking room", String.valueOf(bookingRoomId)));

        LocalDate checkInDate = bookingRoom.getCheckInDate();
        LocalDate checkOutDate = bookingRoom.getCheckOutDate();
        String roomTypeCode = bookingRoom.getRoomTypeCodeSnapshot();

        // Get available rooms
        Set<BookingRoomStatus> blockingStatuses = Set.of(
                BookingRoomStatus.RESERVED,
                BookingRoomStatus.OCCUPIED
        );

        List<Room> availableRooms = roomRepository.findAvailableRooms(
                checkInDate,
                checkOutDate,
                RoomOperationalStatus.ACTIVE,
                blockingStatuses
        ).stream()
                .map(projection -> roomRepository.findById(projection.getRoomId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Filter by room type
        availableRooms = availableRooms.stream()
                .filter(room -> room.getRoomType().getCode().equalsIgnoreCase(roomTypeCode))
                .collect(Collectors.toList());

        // Apply additional filters
        if (floor != null) {
            availableRooms = availableRooms.stream()
                    .filter(room -> floor.equals(room.getFloor()))
                    .collect(Collectors.toList());
        }

        if (housekeepingStatus != null) {
            availableRooms = availableRooms.stream()
                    .filter(room -> housekeepingStatus.equals(room.getHousekeepingStatus()))
                    .collect(Collectors.toList());
        }

        if (viewType != null) {
            availableRooms = availableRooms.stream()
                    .filter(room -> viewType.equals(room.getViewType()))
                    .collect(Collectors.toList());
        }

        // Map to response
        return availableRooms.stream()
                .map(this::mapToAvailableRoom)
                .toList();
    }

    /**
     * Get all floors that have available rooms for a booking room.
     */
    public List<Integer> getAvailableFloors(String bookingPublicId, Long bookingRoomId) {
        Booking booking = bookingRepository.findForStaffDetailByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));

        BookingRoom bookingRoom = booking.getBookingRooms().stream()
                .filter(br -> br.getId().equals(bookingRoomId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking room", String.valueOf(bookingRoomId)));

        String roomTypeCode = bookingRoom.getRoomTypeCodeSnapshot();

        Set<BookingRoomStatus> blockingStatuses = Set.of(
                BookingRoomStatus.RESERVED,
                BookingRoomStatus.OCCUPIED
        );

        return roomRepository.findAvailableRooms(
                        bookingRoom.getCheckInDate(),
                        bookingRoom.getCheckOutDate(),
                        RoomOperationalStatus.ACTIVE,
                        blockingStatuses
                ).stream()
                .map(projection -> roomRepository.findById(projection.getRoomId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(room -> room.getRoomType().getCode().equalsIgnoreCase(roomTypeCode))
                .map(Room::getFloor)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Confirm a pending booking.
     */
    @Transactional
    public BookingConfirmResponse confirmBooking(String publicId, Long staffId) {
        Booking booking = bookingRepository.findForUpdateByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", publicId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessValidationException("Only pending bookings can be confirmed");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);

        // Add status history
        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(BookingStatus.PENDING)
                .toStatus(BookingStatus.CONFIRMED)
                .actorType(ActorType.USER)
                .changedBy(staffId)
                .source(StatusChangeSource.MANUAL)
                .build();
        booking.getStatusHistory().add(history);

        booking = bookingRepository.saveAndFlush(booking);

        return new BookingConfirmResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getConfirmedAt()
        );
    }

    private BookingListResponse.BookingStats getBookingStats() {
        return new BookingListResponse.BookingStats(
                bookingRepository.count(),
                bookingRepository.countByStatus(BookingStatus.PENDING),
                bookingRepository.countByStatus(BookingStatus.CONFIRMED),
                bookingRepository.countByStatus(BookingStatus.CHECKED_IN),
                bookingRepository.countByStatus(BookingStatus.CHECKED_OUT),
                bookingRepository.countByStatus(BookingStatus.CANCELLED)
        );
    }

    private BookingListItemResponse mapToListItem(Booking booking) {
        List<BookingListItemResponse.BookingRoomSummary> roomSummaries = booking.getBookingRooms().stream()
                .map(br -> new BookingListItemResponse.BookingRoomSummary(
                        br.getId(),
                        br.getRoom() != null ? br.getRoom().getRoomNumber() : null,
                        br.getRoomTypeCodeSnapshot(),
                        br.getRoomTypeNameSnapshot(),
                        br.getCheckInDate(),
                        br.getCheckOutDate(),
                        (int) java.time.temporal.ChronoUnit.DAYS.between(br.getCheckInDate(), br.getCheckOutDate()),
                        br.getRoomSubtotal(),
                        br.getStatus().name()
                ))
                .toList();

        LocalDate earliestCheckIn = roomSummaries.stream()
                .map(BookingListItemResponse.BookingRoomSummary::checkInDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        LocalDate latestCheckOut = roomSummaries.stream()
                .map(BookingListItemResponse.BookingRoomSummary::checkOutDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        int totalNights = roomSummaries.stream()
                .mapToInt(BookingListItemResponse.BookingRoomSummary::nights)
                .sum();

        boolean allRoomsAssigned = booking.getBookingRooms().stream()
                .allMatch(br -> br.getRoom() != null);

        return new BookingListItemResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getSource().getCode(),
                booking.getSource().getName(),
                booking.getContactName(),
                booking.getContactEmail(),
                booking.getContactPhone(),
                booking.getAdults(),
                booking.getChildren(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getHoldExpiresAt(),
                booking.getCreatedAt(),
                roomSummaries,
                new BookingListItemResponse.BookingDatesSummary(
                        earliestCheckIn,
                        latestCheckOut,
                        totalNights
                ),
                allRoomsAssigned
        );
    }

    private BookingStaffDetailResponse mapToStaffDetailResponse(Booking booking) {
        // Map rooms
        List<BookingStaffDetailResponse.BookingRoomDetailResponse> roomDetails = booking.getBookingRooms().stream()
                .map(this::mapToRoomDetail)
                .toList();

        // Map guests
        List<BookingStaffDetailResponse.BookingGuestResponse> guestResponses = booking.getBookingGuests().stream()
                .map(this::mapToGuestResponse)
                .toList();

        // Map folio charges
        List<FolioChargeResponse> folioCharges = folioChargeRepository
                .findAllByBooking_PublicIdOrderByChargedAtAscIdAsc(booking.getPublicId())
                .stream()
                .map(this::mapToFolioChargeResponse)
                .toList();

        // Map payments
        List<PaymentResponse> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getBooking().getId().equals(booking.getId()))
                .map(this::mapToPaymentResponse)
                .toList();

        // Map invoices
        List<InvoiceResponse> invoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getBooking().getId().equals(booking.getId()))
                .map(this::mapToInvoiceResponse)
                .toList();

        // Map status history
        List<BookingStatusHistoryResponse> statusHistory = booking.getStatusHistory().stream()
                .sorted(Comparator.comparing(
                        BookingStatusHistory::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(this::mapToStatusHistoryResponse)
                .toList();

        // Get staff names
        String confirmedByName = getStaffName(booking.getConfirmedAt(), ActorType.USER);
        String checkedInByName = booking.getCheckedInBy() != null
                ? booking.getCheckedInBy().getUser().getFullName()
                : null;
        String checkedOutByName = booking.getCheckedOutBy() != null
                ? booking.getCheckedOutBy().getUser().getFullName()
                : null;

        // Customer info
        Long customerId = null;
        String customerName = null;
        String customerEmail = null;
        String customerPhone = null;
        Integer customerLoyaltyPoints = null;

        if (booking.getCustomerProfile() != null) {
            CustomerProfile cp = booking.getCustomerProfile();
            customerId = cp.getId();
            if (cp.getUser() != null) {
                customerName = cp.getUser().getFullName();
                customerEmail = cp.getUser().getEmail();
                customerPhone = cp.getUser().getPhone();
            }
            customerLoyaltyPoints = cp.getLoyaltyPoints();
        }

        return new BookingStaffDetailResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getSource().getCode(),
                booking.getSource().getName(),
                booking.getSourceCommissionPercentSnapshot() != null
                        ? booking.getSourceCommissionPercentSnapshot().toString() : null,
                booking.getContactName(),
                booking.getContactEmail(),
                booking.getContactPhone(),
                null, // contactAddress - not in entity
                booking.getAdults(),
                booking.getChildren(),
                booking.getRoomsTotal(),
                booking.getServicesTotal(),
                booking.getDiscountTotal(),
                booking.getTaxTotal(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getDepositPercentSnapshot(),
                booking.getRequiredDepositAmount(),
                booking.getPaidAmount(),
                booking.getRefundedAmount(),
                booking.getSpecialRequests(),
                booking.getInternalNotes(),
                booking.getHoldExpiresAt(),
                booking.getConfirmedAt(),
                confirmedByName,
                booking.getCheckedInAt(),
                checkedInByName,
                booking.getCheckedOutAt(),
                checkedOutByName,
                booking.getCancelledAt(),
                booking.getCancelledBy(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                customerId,
                customerName,
                customerEmail,
                customerPhone,
                customerLoyaltyPoints,
                roomDetails,
                guestResponses,
                folioCharges,
                payments,
                invoices,
                statusHistory
        );
    }

    private BookingStaffDetailResponse.BookingRoomDetailResponse mapToRoomDetail(BookingRoom br) {
        String assignedByName = br.getAssignedBy() == null
                ? null
                : userRepository.findById(br.getAssignedBy())
                        .map(User::getFullName)
                        .orElse(null);

        return new BookingStaffDetailResponse.BookingRoomDetailResponse(
                br.getId(),
                br.getRoom() != null ? br.getRoom().getId() : null,
                br.getRoom() != null ? br.getRoom().getRoomNumber() : null,
                br.getRoomTypeCodeSnapshot(),
                br.getRoomTypeNameSnapshot(),
                br.getCheckInDate(),
                br.getCheckOutDate(),
                (int) java.time.temporal.ChronoUnit.DAYS.between(br.getCheckInDate(), br.getCheckOutDate()),
                br.getRoomSubtotal(),
                br.getStatus().name(),
                br.getGuestCount(),
                br.getCancellationPolicy() != null ? br.getCancellationPolicy().getCode() : null,
                br.getCancellationPolicy() != null ? br.getCancellationPolicy().getName() : null,
                br.getCancellationPolicySnapshot(),
                br.getPaymentOption() != null ? br.getPaymentOption().name() : null,
                br.getPriceAdjustmentPercentSnapshot(),
                br.getAssignedAt(),
                assignedByName,
                br.getBookingRoomNights().stream()
                        .sorted(Comparator.comparing(BookingRoomNight::getStayDate))
                        .map(night -> new BookingStaffDetailResponse.BookingRoomNightResponse(
                                night.getStayDate(),
                                night.getPrice()
                        ))
                        .toList()
        );
    }

    private BookingStaffDetailResponse.BookingGuestResponse mapToGuestResponse(BookingGuest guest) {
        return new BookingStaffDetailResponse.BookingGuestResponse(
                guest.getId(),
                guest.getFullName(),
                guest.getNationality(),
                guest.getIdDocumentType() != null ? guest.getIdDocumentType().name() : null,
                guest.getIdDocumentNumberEncrypted() != null,
                guest.getDateOfBirth() != null ? guest.getDateOfBirth().toString() : null,
                guest.getCreatedAt()
        );
    }

    private FolioChargeResponse mapToFolioChargeResponse(FolioCharge charge) {
        return new FolioChargeResponse(
                charge.getId(),
                charge.getBooking().getPublicId(),
                charge.getServiceItem() != null ? charge.getServiceItem().getCode() : null,
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getLineSubtotal(),
                charge.getDiscountAmount(),
                charge.getTaxPercent(),
                charge.getTaxAmount(),
                charge.getLineTotal(),
                charge.getChargedAt(),
                charge.getChargedBy(),
                charge.getIsVoided(),
                charge.getVoidedAt(),
                charge.getVoidedBy(),
                charge.getVoidReason()
        );
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentCode(),
                payment.getBooking().getPublicId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                null, // paymentUrl
                null, // deeplink
                null, // qrCodeValue
                null, // checkoutFields
                payment.getExpiresAt(),
                payment.getCreatedAt()
        );
    }

    private InvoiceResponse mapToInvoiceResponse(Invoice invoice) {
        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(item -> new InvoiceItemResponse(
                        item.getId(),
                        item.getLineType(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineSubtotal(),
                        item.getDiscountAmount(),
                        item.getTaxPercent(),
                        item.getTaxAmount(),
                        item.getLineTotal(),
                        item.getReferenceType(),
                        item.getReferenceId(),
                        item.getSortOrder()
                ))
                .toList();

        return new InvoiceResponse(
                invoice.getPublicId(),
                invoice.getInvoiceNumber(),
                invoice.getBooking().getPublicId(),
                invoice.getStatus(),
                invoice.getPaymentStatus(),
                invoice.getIssuedAt(),
                invoice.getIssuedBy(),
                invoice.getBuyerName(),
                invoice.getBuyerAddress(),
                invoice.getBuyerTaxCode(),
                invoice.getBuyerEmail(),
                invoice.getSubtotal(),
                invoice.getDiscountTotal(),
                invoice.getTaxTotal(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getRefundedAmount(),
                invoice.getCurrency(),
                items,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }

    private BookingStatusHistoryResponse mapToStatusHistoryResponse(BookingStatusHistory history) {
        return new BookingStatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getActorType(),
                history.getSource(),
                history.getReason(),
                history.getCreatedAt()
        );
    }

    private AvailableRoomForAssignmentResponse mapToAvailableRoom(Room room) {
        return new AvailableRoomForAssignmentResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType().getCode(),
                room.getRoomType().getName(),
                room.getFloor(),
                room.getViewType(),
                room.getHousekeepingStatus(),
                room.getOperationalStatus(),
                room.getPriceOverride(),
                room.getPriceOverride() // effective price would be calculated from room type base price
        );
    }

    private String getStaffName(OffsetDateTime timestamp, ActorType actorType) {
        // For now, return null - can be enhanced later
        return null;
    }
}
