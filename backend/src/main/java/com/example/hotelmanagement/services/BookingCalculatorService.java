package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.PricingConfigurationException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
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

    private final HotelSettingsRepository hotelSettingsRepository;
    private final RateEngineService rateEngineService;
    private final BookingOptionResolverService bookingOptionResolverService;

    public BookingCalculatorService(
            HotelSettingsRepository hotelSettingsRepository,
            RateEngineService rateEngineService,
            BookingOptionResolverService bookingOptionResolverService
    ) {
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.rateEngineService = rateEngineService;
        this.bookingOptionResolverService = bookingOptionResolverService;
    }

    public BookingPriceCalculationResponse calculatePrice(
            @Valid BookingPriceCalculationRequest request
    ) {
        validateRequest(request);

        BookingOptionSelection selection = bookingOptionResolverService.resolve(
                request.roomTypeCode(),
                request.paymentOption(),
                request.cancellationPolicyCode()
        );
        RoomType roomType = selection.roomType();
        validateRoomCanBeBooked(roomType);
        validateOccupancy(request, roomType);

        List<DailyRateResponse> dailyRates = applyPriceAdjustment(rateEngineService.calculateDailyRatesForRoomType(
                roomType,
                request.checkInDate(),
                request.checkOutDate()
        ), selection.priceAdjustmentPercent());
        BigDecimal roomsTotal = dailyRates.stream()
                .map(DailyRateResponse::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxPercent = getRoomTaxPercent();
        BigDecimal taxTotal = roomsTotal.multiply(taxPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = roomsTotal.add(taxTotal).setScale(2, RoundingMode.HALF_UP);

        return new BookingPriceCalculationResponse(
                null,
                roomType.getId(),
                roomType.getCode(),
                selection.paymentOption(),
                selection.cancellationPolicy().getCode(),
                selection.cancellationPolicy().getName(),
                selection.priceAdjustmentPercent(),
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
        if (request.roomTypeCode() == null || request.roomTypeCode().isBlank()) {
            throw new BusinessValidationException("Room type code is required");
        }
        if (request.paymentOption() == null) {
            throw new BusinessValidationException("Payment option is required");
        }
        if (request.cancellationPolicyCode() == null || request.cancellationPolicyCode().isBlank()) {
            throw new BusinessValidationException("Cancellation policy code is required");
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

    private void validateRoomCanBeBooked(RoomType roomType) {
        if (!Boolean.TRUE.equals(roomType.getIsActive())) {
            throw new BusinessValidationException("Only active room types can be priced for booking");
        }
    }

    private void validateOccupancy(
            BookingPriceCalculationRequest request,
            RoomType roomType
    ) {
        int totalGuests = request.adults() + request.children();
        Integer maxOccupancy = roomType.getMaxOccupancy();
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

    private List<DailyRateResponse> applyPriceAdjustment(
            List<DailyRateResponse> dailyRates,
            BigDecimal adjustmentPercent
    ) {
        BigDecimal normalizedAdjustment = adjustmentPercent == null ? BigDecimal.ZERO : adjustmentPercent;
        if (normalizedAdjustment.signum() == 0) {
            return dailyRates;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(normalizedAdjustment.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
        return dailyRates.stream()
                .map(dailyRate -> new DailyRateResponse(
                        dailyRate.date(),
                        dailyRate.price().multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
