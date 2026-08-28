package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.booking.BookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.booking.BookingPriceCalculationResponse;
import com.example.hotelmanagement.dto.booking.StaffBookingPriceCalculationRequest;
import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.PricingConfigurationException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCalculatorServiceTest {

    private static final Long ROOM_TYPE_ID = 20L;
    private static final String ROOM_TYPE_CODE = "DLX";
    private static final String POLICY_CODE = "FLEXIBLE";
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDate CHECK_OUT_DATE = LocalDate.of(2026, 8, 23);

    @Mock
    private BookingOptionResolverService bookingOptionResolverService;

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @Mock
    private RateEngineService rateEngineService;

    private BookingCalculatorService bookingCalculatorService;

    @BeforeEach
    void setUp() {
        bookingCalculatorService = new BookingCalculatorService(
                hotelSettingsRepository,
                rateEngineService,
                bookingOptionResolverService
        );
    }

    @Test
    void calculatePriceReturnsRoomInvoicePreview() {
        RoomType roomType = createRoomType(2, 2, 4);
        BookingPriceCalculationRequest request = createRequest(2, 1);
        when(bookingOptionResolverService.resolve(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE, POLICY_CODE))
                .thenReturn(createSelection(roomType));
        when(rateEngineService.calculateDailyRatesForRoomType(roomType, CHECK_IN_DATE, CHECK_OUT_DATE))
                .thenReturn(List.of(
                        new DailyRateResponse(CHECK_IN_DATE, money("1000.00")),
                        new DailyRateResponse(CHECK_IN_DATE.plusDays(1), money("1500.00"))
                ));
        when(hotelSettingsRepository.getDecimalValue("default_room_tax_percent"))
                .thenReturn(money("10.00"));

        BookingPriceCalculationResponse response = bookingCalculatorService.calculatePrice(request);

        assertNull(response.roomId());
        assertEquals(ROOM_TYPE_ID, response.roomTypeId());
        assertEquals(ROOM_TYPE_CODE, response.roomTypeCode());
        assertEquals(POLICY_CODE, response.cancellationPolicyCode());
        assertEquals(2, response.nights());
        assertEquals(money("2500.00"), response.roomsTotal());
        assertEquals(money("10.00"), response.roomTaxPercentSnapshot());
        assertEquals(money("250.00"), response.taxTotal());
        assertEquals(money("2750.00"), response.totalAmount());
        assertEquals("VND", response.currency());
        assertEquals(2, response.dailyRates().size());
    }

    @Test
    void calculateStaffPriceUsesNonRefundPolicyWithoutRoomTypePolicyOption() {
        RoomType roomType = createRoomType(2, 0, 2);
        CancellationPolicy nonRefundPolicy = CancellationPolicy.builder()
                .code("NON_REFUND")
                .name("Non-refundable")
                .priceAdjustmentPercent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        when(bookingOptionResolverService.resolveStaffBooking(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE))
                .thenReturn(new BookingOptionSelection(
                        roomType,
                        BookingPaymentOption.ONLINE,
                        nonRefundPolicy,
                        BigDecimal.ZERO
                ));
        when(rateEngineService.calculateDailyRatesForRoomType(roomType, CHECK_IN_DATE, CHECK_OUT_DATE))
                .thenReturn(List.of(
                        new DailyRateResponse(CHECK_IN_DATE, money("1000.00")),
                        new DailyRateResponse(CHECK_IN_DATE.plusDays(1), money("1000.00"))
                ));
        when(hotelSettingsRepository.getDecimalValue("default_room_tax_percent"))
                .thenReturn(null);

        BookingPriceCalculationResponse response = bookingCalculatorService.calculateStaffPrice(
                new StaffBookingPriceCalculationRequest(
                        ROOM_TYPE_CODE,
                        BookingPaymentOption.ONLINE,
                        CHECK_IN_DATE,
                        CHECK_OUT_DATE,
                        1
                )
        );

        assertEquals("NON_REFUND", response.cancellationPolicyCode());
        assertEquals(0, response.children());
        verify(bookingOptionResolverService).resolveStaffBooking(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE);
    }

    @Test
    void calculatePriceUsesZeroTaxWhenSettingIsMissing() {
        RoomType roomType = createRoomType(2, 2, 4);
        BookingPriceCalculationRequest request = createRequest(2, 0);
        when(bookingOptionResolverService.resolve(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE, POLICY_CODE))
                .thenReturn(createSelection(roomType));
        when(rateEngineService.calculateDailyRatesForRoomType(roomType, CHECK_IN_DATE, CHECK_OUT_DATE))
                .thenReturn(List.of(
                        new DailyRateResponse(CHECK_IN_DATE, money("1000.00")),
                        new DailyRateResponse(CHECK_IN_DATE.plusDays(1), money("1000.00"))
                ));
        when(hotelSettingsRepository.getDecimalValue("default_room_tax_percent"))
                .thenReturn(null);

        BookingPriceCalculationResponse response = bookingCalculatorService.calculatePrice(request);

        assertEquals(money("0.00"), response.roomTaxPercentSnapshot());
        assertEquals(money("0.00"), response.taxTotal());
        assertEquals(money("2000.00"), response.totalAmount());
    }

    @Test
    void calculatePriceRejectsInvalidDatesBeforeLoadingRoom() {
        BookingPriceCalculationRequest request = new BookingPriceCalculationRequest(
                ROOM_TYPE_CODE,
                BookingPaymentOption.ONLINE,
                POLICY_CODE,
                CHECK_IN_DATE,
                CHECK_IN_DATE,
                1,
                0
        );

        assertThrows(
                BusinessValidationException.class,
                () -> bookingCalculatorService.calculatePrice(request)
        );

        verify(bookingOptionResolverService, never()).resolve(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE, POLICY_CODE);
    }

    @Test
    void calculatePriceRejectsGuestCountAboveOccupancy() {
        RoomType roomType = createRoomType(2, 2, 2);
        BookingPriceCalculationRequest request = createRequest(2, 1);
        when(bookingOptionResolverService.resolve(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE, POLICY_CODE))
                .thenReturn(createSelection(roomType));

        assertThrows(
                BusinessValidationException.class,
                () -> bookingCalculatorService.calculatePrice(request)
        );

        verify(rateEngineService, never()).calculateDailyRatesForRoomType(roomType, CHECK_IN_DATE, CHECK_OUT_DATE);
    }

    @Test
    void calculatePriceRejectsInvalidTaxPercent() {
        RoomType roomType = createRoomType(2, 2, 4);
        BookingPriceCalculationRequest request = createRequest(1, 0);
        when(bookingOptionResolverService.resolve(ROOM_TYPE_CODE, BookingPaymentOption.ONLINE, POLICY_CODE))
                .thenReturn(createSelection(roomType));
        when(rateEngineService.calculateDailyRatesForRoomType(roomType, CHECK_IN_DATE, CHECK_OUT_DATE))
                .thenReturn(List.of(new DailyRateResponse(CHECK_IN_DATE, money("1000.00"))));
        when(hotelSettingsRepository.getDecimalValue("default_room_tax_percent"))
                .thenReturn(money("120.00"));

        assertThrows(
                PricingConfigurationException.class,
                () -> bookingCalculatorService.calculatePrice(request)
        );
    }

    private BookingPriceCalculationRequest createRequest(int adults, int children) {
        return new BookingPriceCalculationRequest(
                ROOM_TYPE_CODE,
                BookingPaymentOption.ONLINE,
                POLICY_CODE,
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                adults,
                children
        );
    }

    private RoomType createRoomType(
            int maxAdults,
            int maxChildren,
            int maxOccupancy
    ) {
        RoomType roomType = RoomType.builder()
                .code("DLX")
                .name("Deluxe")
                .slug("deluxe")
                .bedCount(1)
                .maxOccupancy(maxOccupancy)
                .maxAdults(maxAdults)
                .maxChildren(maxChildren)
                .basePrice(money("1000.00"))
                .currency("VND")
                .isActive(true)
                .build();
        roomType.setId(ROOM_TYPE_ID);
        return roomType;
    }

    private BookingOptionSelection createSelection(RoomType roomType) {
        CancellationPolicy policy = CancellationPolicy.builder()
                .code(POLICY_CODE)
                .name("Flexible")
                .build();
        return new BookingOptionSelection(
                roomType,
                BookingPaymentOption.ONLINE,
                policy,
                BigDecimal.ZERO
        );
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
