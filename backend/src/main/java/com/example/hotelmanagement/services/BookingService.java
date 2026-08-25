package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingCreateRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.dto.booking.BookingResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomCreateItem;
import com.example.hotelmanagement.dto.booking.BookingRoomNightResponse;
import com.example.hotelmanagement.dto.booking.BookingRoomResponse;
import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingGuest;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.BookingSource;
import com.example.hotelmanagement.entity.BookingStatusHistory;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;
import com.example.hotelmanagement.exceptions.BookingRoomConflictException;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingGuestRepository;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.BookingSourceRepository;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Validated
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /**
     * Self-service bookings created through this API always originate from the website channel;
     * staff-assisted channels (WALK_IN/PHONE) are out of scope until STAFF is granted booking:create.
     */
    private static final String WEBSITE_SOURCE_CODE = "WEBSITE";
    private static final String CUSTOMER_REMOVED_PENDING_BOOKING_REASON =
            "Customer removed pending booking before payment";
    private static final Duration HOLD_DURATION = Duration.ofMinutes(15);
    private static final Set<BookingRoomStatus> ACTIVE_BOOKING_STATUSES =
            Set.of(BookingRoomStatus.RESERVED, BookingRoomStatus.OCCUPIED);
    private static final int BOOKING_CODE_MAX_ATTEMPTS = 5;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final BookingRepository bookingRepository;
    private final BookingGuestRepository bookingGuestRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final BookingSourceRepository bookingSourceRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final RoomRepository roomRepository;
    private final BookingCalculatorService bookingCalculatorService;
    private final CancellationPolicyService cancellationPolicyService;
    private final BookingOptionResolverService bookingOptionResolverService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            BookingGuestRepository bookingGuestRepository,
            BookingRoomRepository bookingRoomRepository,
            BookingSourceRepository bookingSourceRepository,
            CustomerProfileRepository customerProfileRepository,
            HotelSettingsRepository hotelSettingsRepository,
            RoomRepository roomRepository,
            BookingCalculatorService bookingCalculatorService,
            CancellationPolicyService cancellationPolicyService,
            BookingOptionResolverService bookingOptionResolverService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingGuestRepository = bookingGuestRepository;
        this.bookingRoomRepository = bookingRoomRepository;
        this.bookingSourceRepository = bookingSourceRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.roomRepository = roomRepository;
        this.bookingCalculatorService = bookingCalculatorService;
        this.cancellationPolicyService = cancellationPolicyService;
        this.bookingOptionResolverService = bookingOptionResolverService;
        this.clock = clock;
    }

    public BookingService(
            BookingRepository bookingRepository,
            BookingRoomRepository bookingRoomRepository,
            BookingGuestRepository bookingGuestRepository,
            BookingSourceRepository bookingSourceRepository,
            CustomerProfileRepository customerProfileRepository,
            HotelSettingsRepository hotelSettingsRepository,
            RoomRepository roomRepository,
            BookingCalculatorService bookingCalculatorService,
            CancellationPolicyService cancellationPolicyService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingGuestRepository = bookingGuestRepository;
        this.bookingRoomRepository = bookingRoomRepository;
        this.bookingSourceRepository = bookingSourceRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.roomRepository = roomRepository;
        this.bookingCalculatorService = bookingCalculatorService;
        this.cancellationPolicyService = cancellationPolicyService;
        this.bookingOptionResolverService = null;
        this.clock = clock;
    }

    public BookingService(
            BookingRepository bookingRepository,
            BookingRoomRepository bookingRoomRepository,
            BookingSourceRepository bookingSourceRepository,
            CustomerProfileRepository customerProfileRepository,
            HotelSettingsRepository hotelSettingsRepository,
            RoomRepository roomRepository,
            BookingCalculatorService bookingCalculatorService,
            CancellationPolicyService cancellationPolicyService,
            Clock clock
    ) {
        this(
                bookingRepository,
                bookingRoomRepository,
                null,
                bookingSourceRepository,
                customerProfileRepository,
                hotelSettingsRepository,
                roomRepository,
                bookingCalculatorService,
                cancellationPolicyService,
                clock
        );
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public BookingResponse createBooking(@Valid BookingCreateRequest request, Long userId) {
        CustomerProfile customerProfile = customerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessValidationException(
                        "A customer profile is required to create a booking"
                ));
        BookingSource source = bookingSourceRepository
                .findByCodeIgnoreCaseAndIsActiveTrue(WEBSITE_SOURCE_CODE)
                .orElseThrow(() -> new ResourceNotFoundException("Booking source", WEBSITE_SOURCE_CODE));

        User user = customerProfile.getUser();
        Booking booking = Booking.builder()
                .publicId(java.util.UUID.randomUUID().toString())
                .bookingCode(generateBookingCode())
                .customerProfile(customerProfile)
                .source(source)
                .sourceCommissionPercentSnapshot(source.getCommissionPercent())
                .status(BookingStatus.PENDING)
                .contactName(normalizeOrDefault(request.contactName(), user.getFullName()))
                .contactEmail(normalizeOrDefault(request.contactEmail(), user.getEmail()))
                .contactPhone(normalizeOrDefault(request.contactPhone(), user.getPhone()))
                .specialRequests(normalizeOptionalText(request.specialRequests()))
                .holdExpiresAt(OffsetDateTime.now(clock).plus(HOLD_DURATION))
                .createdBy(userId)
                .build();

        BigDecimal roomsTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal roomTaxPercentSnapshot = null;
        String currency = null;
        int totalAdults = 0;
        int totalChildren = 0;

        for (BookingRoomCreateItem item : request.rooms()) {
            Room room = assignAvailableRoom(item);
            BookingOptionSelection optionSelection = resolveBookingOption(item, room);
            BookingPriceCalculationResponse priceCalculation = bookingCalculatorService.calculatePrice(
                    new BookingPriceCalculationRequest(
                            item.roomTypeCode(),
                            item.paymentOption(),
                            item.cancellationPolicyCode(),
                            item.checkInDate(),
                            item.checkOutDate(),
                            item.adults(),
                            item.children()
                    )
            );
            RoomType roomType = optionSelection.roomType();

            BookingRoom bookingRoom = BookingRoom.builder()
                    .booking(booking)
                    .room(room)
                    .roomType(roomType)
                    .roomTypeCodeSnapshot(roomType.getCode())
                    .roomTypeNameSnapshot(roomType.getName())
                    .checkInDate(item.checkInDate())
                    .checkOutDate(item.checkOutDate())
                    .roomSubtotal(priceCalculation.roomsTotal())
                    .paymentOption(optionSelection.paymentOption())
                    .priceAdjustmentPercentSnapshot(optionSelection.priceAdjustmentPercent())
                    .status(BookingRoomStatus.RESERVED)
                    .guestCount(item.adults() + item.children())
                    .build();
            if (bookingOptionResolverService == null) {
                cancellationPolicyService.applyPolicySnapshot(bookingRoom, optionSelection.cancellationPolicy());
            } else {
                cancellationPolicyService.applyPolicySnapshot(
                        bookingRoom,
                        optionSelection.cancellationPolicy(),
                        optionSelection.priceAdjustmentPercent()
                );
            }

            for (DailyRateResponse dailyRate : priceCalculation.dailyRates()) {
                bookingRoom.getBookingRoomNights().add(
                        BookingRoomNight.builder()
                                .bookingRoom(bookingRoom)
                                .stayDate(dailyRate.date())
                                .price(dailyRate.price())
                                .build()
                );
            }

            booking.getBookingRooms().add(bookingRoom);
            booking.getBookingGuests().add(
                    BookingGuest.builder()
                            .booking(booking)
                            .bookingRoom(bookingRoom)
                            .fullName(normalizeOrDefault(item.guestFullName(), booking.getContactName()))
                            .build()
            );
            roomsTotal = roomsTotal.add(priceCalculation.roomsTotal());
            taxTotal = taxTotal.add(priceCalculation.taxTotal());
            totalAdults += item.adults();
            totalChildren += item.children();
            if (roomTaxPercentSnapshot == null) {
                roomTaxPercentSnapshot = priceCalculation.roomTaxPercentSnapshot();
            }
            if (currency == null) {
                currency = priceCalculation.currency();
            }
        }

        booking.setAdults(totalAdults);
        booking.setChildren(totalChildren);
        booking.setRoomsTotal(roomsTotal.setScale(2, RoundingMode.HALF_UP));
        booking.setTaxTotal(taxTotal.setScale(2, RoundingMode.HALF_UP));
        booking.setRoomTaxPercentSnapshot(roomTaxPercentSnapshot);
        booking.setTotalAmount(roomsTotal.add(taxTotal).setScale(2, RoundingMode.HALF_UP));
        BigDecimal depositPercentSnapshot = getDefaultDepositPercent();
        booking.setDepositPercentSnapshot(depositPercentSnapshot);
        booking.setRequiredDepositAmount(calculateRequiredDepositAmount(
                booking.getTotalAmount(),
                depositPercentSnapshot
        ));
        if (currency != null) {
            booking.setCurrency(currency);
        }

        booking.getStatusHistory().add(
                BookingStatusHistory.builder()
                        .booking(booking)
                        .fromStatus(null)
                        .toStatus(BookingStatus.PENDING)
                        .actorType(ActorType.USER)
                        .changedBy(userId)
                        .source(StatusChangeSource.MANUAL)
                        .build()
        );

        try {
            return mapResponse(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Database rejected booking creation bookingCode={}",
                    booking.getBookingCode(),
                    exception
            );
            throw new BookingRoomConflictException(
                    "One or more selected rooms are no longer available for the requested dates",
                    exception
            );
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public List<BookingResponse> getMyBookings(Long userId) {
        return bookingRepository.findAllByCustomerProfile_User_IdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(booking -> !isCustomerRemovedPendingBooking(booking))
                .map(this::mapResponse)
                .toList();
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public BookingResponse removePendingBookingRoom(
            String bookingPublicId,
            Long bookingRoomId,
            Long userId
    ) {
        Booking booking = getPendingCustomerBookingForUpdate(bookingPublicId, userId);
        BookingRoom bookingRoom = booking.getBookingRooms().stream()
                .filter(room -> bookingRoomId.equals(room.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking room",
                        String.valueOf(bookingRoomId)
                ));

        if (booking.getBookingRooms().size() == 1) {
            deletePendingBooking(bookingPublicId, userId);
            return null;
        }

        booking.getBookingGuests().removeIf(guest ->
                guest.getBookingRoom() != null && bookingRoomId.equals(guest.getBookingRoom().getId())
        );
        bookingGuestRepository.deleteAllByBookingRoomId(bookingRoomId);
        booking.getBookingRooms().remove(bookingRoom);
        bookingRoomRepository.delete(bookingRoom);

        recalculatePendingBookingTotals(booking);
        return mapResponse(bookingRepository.saveAndFlush(booking));
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public void deletePendingBooking(String bookingPublicId, Long userId) {
        Booking booking = getPendingCustomerBookingForUpdate(bookingPublicId, userId);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(OffsetDateTime.now(clock));
        booking.setCancelledBy(userId);
        booking.setCancellationReason(CUSTOMER_REMOVED_PENDING_BOOKING_REASON);
        bookingRepository.saveAndFlush(booking);
    }

    private boolean isCustomerRemovedPendingBooking(Booking booking) {
        return booking.getStatus() == BookingStatus.CANCELLED
                && CUSTOMER_REMOVED_PENDING_BOOKING_REASON.equals(booking.getCancellationReason());
    }

    private Booking getPendingCustomerBookingForUpdate(String bookingPublicId, Long userId) {
        Booking booking = bookingRepository
                .findByPublicIdAndCustomerProfile_User_Id(bookingPublicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessValidationException("Only pending bookings can be changed before payment");
        }
        if (booking.getPaymentStatus() != BookingPaymentStatus.UNPAID
                || booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessValidationException("Bookings with received payments cannot be deleted here");
        }
        return booking;
    }

    private void recalculatePendingBookingTotals(Booking booking) {
        BigDecimal roomsTotal = booking.getBookingRooms().stream()
                .map(BookingRoom::getRoomSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxPercent = booking.getRoomTaxPercentSnapshot() == null
                ? BigDecimal.ZERO
                : booking.getRoomTaxPercentSnapshot();
        BigDecimal taxTotal = roomsTotal.multiply(taxPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal servicesTotal = booking.getServicesTotal() == null
                ? BigDecimal.ZERO
                : booking.getServicesTotal();
        BigDecimal discountTotal = booking.getDiscountTotal() == null
                ? BigDecimal.ZERO
                : booking.getDiscountTotal();
        BigDecimal totalAmount = roomsTotal.add(servicesTotal)
                .add(taxTotal)
                .subtract(discountTotal)
                .setScale(2, RoundingMode.HALF_UP);

        int adults = Math.max(1, booking.getBookingRooms().stream()
                .mapToInt(room -> room.getGuestCount() == null ? 0 : room.getGuestCount())
                .sum());

        booking.setAdults(adults);
        booking.setChildren(0);
        booking.setRoomsTotal(roomsTotal);
        booking.setTaxTotal(taxTotal);
        booking.setTotalAmount(totalAmount);
    }

    private Room assignAvailableRoom(BookingRoomCreateItem item) {
        if (!item.checkOutDate().isAfter(item.checkInDate())) {
            throw new BusinessValidationException("Check-out date must be after check-in date");
        }
        Room assignedRoom = roomRepository.findAvailableRoomsByTypeForUpdate(
                item.roomTypeCode(),
                item.checkInDate(),
                item.checkOutDate(),
                RoomOperationalStatus.ACTIVE,
                ACTIVE_BOOKING_STATUSES
        ).stream().findFirst().orElse(null);
        if (assignedRoom != null) {
            return assignedRoom;
        }
        Long legacyRoomId = item.roomId();
        if (legacyRoomId != null) {
            if (bookingRoomRepository != null && bookingRoomRepository.existsOverlappingBooking(
                    legacyRoomId,
                    ACTIVE_BOOKING_STATUSES,
                    item.checkInDate(),
                    item.checkOutDate()
            )) {
                throw new BookingRoomConflictException(
                        "Room " + legacyRoomId + " is not available for the requested dates"
                );
            }
            return roomRepository.findByIdAndDeletedAtIsNull(legacyRoomId)
                    .orElseThrow(() -> new BookingRoomConflictException(
                            "No rooms are available for the requested room type and dates"
                    ));
        }
        throw new BookingRoomConflictException("No rooms are available for the requested room type and dates");
    }

    private BookingOptionSelection resolveBookingOption(BookingRoomCreateItem item, Room room) {
        if (bookingOptionResolverService != null) {
            return bookingOptionResolverService.resolve(
                    item.roomTypeCode(),
                    item.paymentOption(),
                    item.cancellationPolicyCode()
            );
        }
        return new BookingOptionSelection(
                room.getRoomType(),
                item.paymentOption(),
                room.getRoomType().getCancellationPolicy(),
                BigDecimal.ZERO
        );
    }

    private String generateBookingCode() {
        int year = LocalDate.now(clock).getYear();
        for (int attempt = 0; attempt < BOOKING_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = "BK-" + year + "-" + String.format("%06d", secureRandom.nextInt(1_000_000));
            if (!bookingRepository.existsByBookingCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessValidationException("Unable to generate a unique booking code, please retry");
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.strip();
    }

    private BigDecimal getDefaultDepositPercent() {
        BigDecimal depositPercent = hotelSettingsRepository
                .getDecimalValue(HotelSettingsService.DEPOSIT_PERCENT_KEY);
        if (depositPercent == null
                || depositPercent.signum() <= 0
                || depositPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessValidationException("Default deposit percentage must be greater than 0 and at most 100");
        }
        return depositPercent.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRequiredDepositAmount(BigDecimal totalAmount, BigDecimal depositPercent) {
        return totalAmount.multiply(depositPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.strip();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }

    private BookingResponse mapResponse(Booking booking) {
        return new BookingResponse(
                booking.getPublicId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getSource().getCode(),
                booking.getSourceCommissionPercentSnapshot(),
                booking.getContactName(),
                booking.getContactEmail(),
                booking.getContactPhone(),
                booking.getAdults(),
                booking.getChildren(),
                booking.getRoomsTotal(),
                booking.getTaxTotal(),
                booking.getRoomTaxPercentSnapshot(),
                booking.getTotalAmount(),
                booking.getDepositPercentSnapshot(),
                booking.getRequiredDepositAmount(),
                booking.getCurrency(),
                booking.getHoldExpiresAt(),
                booking.getBookingRooms().stream().map(this::mapRoomResponse).toList(),
                booking.getCreatedAt()
        );
    }

    private BookingRoomResponse mapRoomResponse(BookingRoom bookingRoom) {
        return new BookingRoomResponse(
                bookingRoom.getId(),
                null,
                bookingRoom.getRoomTypeCodeSnapshot(),
                bookingRoom.getRoomTypeNameSnapshot(),
                bookingRoom.getCheckInDate(),
                bookingRoom.getCheckOutDate(),
                bookingRoom.getStatus(),
                bookingRoom.getGuestCount(),
                bookingRoom.getRoomSubtotal(),
                bookingRoom.getCancellationPolicy() == null ? null : bookingRoom.getCancellationPolicy().getCode(),
                bookingRoom.getCancellationPolicy() == null ? null : bookingRoom.getCancellationPolicy().getName(),
                bookingRoom.getPaymentOption(),
                bookingRoom.getPriceAdjustmentPercentSnapshot(),
                bookingRoom.getAssignedAt(),
                bookingRoom.getAssignedBy(),
                bookingRoom.getBookingRoomNights().stream()
                        .sorted(java.util.Comparator.comparing(BookingRoomNight::getStayDate))
                        .map(night -> new BookingRoomNightResponse(night.getStayDate(), night.getPrice()))
                        .toList()
        );
    }
}
