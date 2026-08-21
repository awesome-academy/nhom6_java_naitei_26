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
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.BookingSource;
import com.example.hotelmanagement.entity.BookingStatusHistory;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;
import com.example.hotelmanagement.exceptions.BookingRoomConflictException;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.BookingSourceRepository;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final Duration HOLD_DURATION = Duration.ofMinutes(15);
    private static final Set<BookingRoomStatus> ACTIVE_BOOKING_STATUSES =
            Set.of(BookingRoomStatus.RESERVED, BookingRoomStatus.OCCUPIED);
    private static final int BOOKING_CODE_MAX_ATTEMPTS = 5;

    private final BookingRepository bookingRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final BookingSourceRepository bookingSourceRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RoomRepository roomRepository;
    private final BookingCalculatorService bookingCalculatorService;
    private final CancellationPolicyService cancellationPolicyService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public BookingService(
            BookingRepository bookingRepository,
            BookingRoomRepository bookingRoomRepository,
            BookingSourceRepository bookingSourceRepository,
            CustomerProfileRepository customerProfileRepository,
            RoomRepository roomRepository,
            BookingCalculatorService bookingCalculatorService,
            CancellationPolicyService cancellationPolicyService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingRoomRepository = bookingRoomRepository;
        this.bookingSourceRepository = bookingSourceRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.roomRepository = roomRepository;
        this.bookingCalculatorService = bookingCalculatorService;
        this.cancellationPolicyService = cancellationPolicyService;
        this.clock = clock;
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
            ensureRoomIsAvailable(item);
            BookingPriceCalculationResponse priceCalculation = bookingCalculatorService.calculatePrice(
                    new BookingPriceCalculationRequest(
                            item.roomId(),
                            item.checkInDate(),
                            item.checkOutDate(),
                            item.adults(),
                            item.children()
                    )
            );
            Room room = roomRepository.findByIdAndDeletedAtIsNull(item.roomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", item.roomId().toString()));
            RoomType roomType = room.getRoomType();

            BookingRoom bookingRoom = BookingRoom.builder()
                    .booking(booking)
                    .room(room)
                    .roomType(roomType)
                    .roomTypeCodeSnapshot(roomType.getCode())
                    .roomTypeNameSnapshot(roomType.getName())
                    .checkInDate(item.checkInDate())
                    .checkOutDate(item.checkOutDate())
                    .roomSubtotal(priceCalculation.roomsTotal())
                    .status(BookingRoomStatus.RESERVED)
                    .guestCount(item.adults() + item.children())
                    .build();

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
        if (currency != null) {
            booking.setCurrency(currency);
        }

        cancellationPolicyService.applyPolicySnapshot(booking, request.cancellationPolicyCode());

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

    private void ensureRoomIsAvailable(BookingRoomCreateItem item) {
        if (!item.checkOutDate().isAfter(item.checkInDate())) {
            throw new BusinessValidationException("Check-out date must be after check-in date");
        }
        if (bookingRoomRepository.existsOverlappingBooking(
                item.roomId(),
                ACTIVE_BOOKING_STATUSES,
                item.checkInDate(),
                item.checkOutDate()
        )) {
            throw new BookingRoomConflictException(
                    "Room " + item.roomId() + " is not available for the requested dates"
            );
        }
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
                booking.getCurrency(),
                booking.getCancellationPolicy() == null ? null : booking.getCancellationPolicy().getCode(),
                booking.getHoldExpiresAt(),
                booking.getBookingRooms().stream().map(this::mapRoomResponse).toList(),
                booking.getCreatedAt()
        );
    }

    private BookingRoomResponse mapRoomResponse(BookingRoom bookingRoom) {
        return new BookingRoomResponse(
                bookingRoom.getRoom().getRoomNumber(),
                bookingRoom.getRoomTypeCodeSnapshot(),
                bookingRoom.getRoomTypeNameSnapshot(),
                bookingRoom.getCheckInDate(),
                bookingRoom.getCheckOutDate(),
                bookingRoom.getStatus(),
                bookingRoom.getGuestCount(),
                bookingRoom.getRoomSubtotal(),
                bookingRoom.getBookingRoomNights().stream()
                        .sorted(java.util.Comparator.comparing(BookingRoomNight::getStayDate))
                        .map(night -> new BookingRoomNightResponse(night.getStayDate(), night.getPrice()))
                        .toList()
        );
    }
}
