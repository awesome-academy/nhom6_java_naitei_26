package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.PricingConfigurationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
public class BookingCalculatorService {

    private static final String DEFAULT_ROOM_TAX_PERCENT_KEY = "default_room_tax_percent";
    private static final String DEFAULT_CURRENCY_KEY = "default_currency";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = money("0.00");

    private final RoomRepository roomRepository;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final RateEngineService rateEngineService;

    public BookingCalculatorService(
            RoomRepository roomRepository,
            HotelSettingsRepository hotelSettingsRepository,
            RateEngineService rateEngineService
    ) {
        this.roomRepository = roomRepository;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.rateEngineService = rateEngineService;
    }

    public BookingPriceCalculationResponse calculatePrice(
            @Valid BookingPriceCalculationRequest request
    ) {
        validateRequest(request);

        Room room = roomRepository.findByIdAndDeletedAtIsNull(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", request.roomId().toString()));
        RoomType roomType = getRoomType(room);
        validateRoomCanBeBooked(room, roomType);
        validateOccupancy(request, room, roomType);

        List<DailyRateResponse> dailyRates = rateEngineService.calculateDailyRates(
                request.roomId(),
                request.checkInDate(),
                request.checkOutDate()
        );
        BigDecimal roomsTotal = dailyRates.stream()
                .map(DailyRateResponse::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxPercent = getRoomTaxPercent();
        BigDecimal taxTotal = roomsTotal.multiply(taxPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = roomsTotal.add(taxTotal).setScale(2, RoundingMode.HALF_UP);

        return new BookingPriceCalculationResponse(
                room.getId(),
                roomType.getId(),
                request.checkInDate(),
                request.checkOutDate(),
                ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate()),
                request.adults(),
                request.children(),
                dailyRates,
                roomsTotal,
                taxPercent,
                taxTotal,
                totalAmount,
                resolveCurrency(roomType)
        );
    }

    private void validateRequest(BookingPriceCalculationRequest request) {
        if (request == null) {
            throw new BusinessValidationException("Booking price calculation request is required");
        }
        if (request.roomId() == null || request.roomId() <= 0) {
            throw new BusinessValidationException("Room id must be a positive number");
        }
        if (request.checkInDate() == null || request.checkOutDate() == null) {
            throw new BusinessValidationException("Check-in date and check-out date are required");
        }
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new BusinessValidationException("Check-out date must be after check-in date");
        }
        if (request.adults() == null || request.adults() < 1) {
            throw new BusinessValidationException("At least one adult is required");
        }
        if (request.children() == null || request.children() < 0) {
            throw new BusinessValidationException("Children count cannot be negative");
        }
    }

    private RoomType getRoomType(Room room) {
        if (room.getRoomType() == null || room.getRoomType().getId() == null) {
            throw new PricingConfigurationException(
                    "Room " + room.getId() + " does not have a valid room type"
            );
        }
        return room.getRoomType();
    }

    private void validateRoomCanBeBooked(Room room, RoomType roomType) {
        if (!Boolean.TRUE.equals(room.getIsActive())) {
            throw new BusinessValidationException("Only active rooms can be priced for booking");
        }
        if (!Boolean.TRUE.equals(roomType.getIsActive())) {
            throw new BusinessValidationException("Only active room types can be priced for booking");
        }
    }

    private void validateOccupancy(
            BookingPriceCalculationRequest request,
            Room room,
            RoomType roomType
    ) {
        int totalGuests = request.adults() + request.children();
        Integer maxOccupancy = room.getMaxOccupancyOverride() != null
                ? room.getMaxOccupancyOverride()
                : roomType.getMaxOccupancy();
        if (maxOccupancy == null || maxOccupancy < 1) {
            throw new PricingConfigurationException(
                    "Room type " + roomType.getId() + " has no valid max occupancy"
            );
        }
        if (totalGuests > maxOccupancy) {
            throw new BusinessValidationException("Guest count exceeds room occupancy");
        }
        if (roomType.getMaxAdults() == null || roomType.getMaxAdults() < 1) {
            throw new PricingConfigurationException(
                    "Room type " + roomType.getId() + " has no valid max adults"
            );
        }
        if (request.adults() > roomType.getMaxAdults()) {
            throw new BusinessValidationException("Adult count exceeds room type limit");
        }
        Integer maxChildren = roomType.getMaxChildren() == null ? 0 : roomType.getMaxChildren();
        if (request.children() > maxChildren) {
            throw new BusinessValidationException("Children count exceeds room type limit");
        }
    }

    private BigDecimal getRoomTaxPercent() {
        BigDecimal taxPercent = hotelSettingsRepository.getDecimalValue(DEFAULT_ROOM_TAX_PERCENT_KEY);
        if (taxPercent == null) {
            return ZERO_MONEY;
        }
        if (taxPercent.signum() < 0 || taxPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new PricingConfigurationException("Default room tax percentage must be between 0 and 100");
        }
        return taxPercent.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveCurrency(RoomType roomType) {
        if (roomType.getCurrency() != null && !roomType.getCurrency().isBlank()) {
            return roomType.getCurrency();
        }
        String defaultCurrency = hotelSettingsRepository.getStringValue(DEFAULT_CURRENCY_KEY);
        return defaultCurrency == null || defaultCurrency.isBlank() ? "VND" : defaultCurrency;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
