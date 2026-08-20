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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCalculatorServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ROOM_TYPE_ID = 20L;
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDate CHECK_OUT_DATE = LocalDate.of(2026, 8, 23);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @Mock
    private RateEngineService rateEngineService;

    private BookingCalculatorService bookingCalculatorService;

    @BeforeEach
    void setUp() {
        bookingCalculatorService = new BookingCalculatorService(
                roomRepository,
                hotelSettingsRepository,
                rateEngineService
        );
    }

    @Test
    void calculatePriceReturnsRoomInvoicePreview() {
        Room room = createRoom(2, 2, 4);
        BookingPriceCalculationRequest request = createRequest(2, 1);
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.of(room));
        when(rateEngineService.calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_OUT_DATE))
                .thenReturn(List.of(
                        new DailyRateResponse(CHECK_IN_DATE, money("1000.00")),
                        new DailyRateResponse(CHECK_IN_DATE.plusDays(1), money("1500.00"))
                ));
        when(hotelSettingsRepository.getDecimalValue("default_room_tax_percent"))
                .thenReturn(money("10.00"));

        BookingPriceCalculationResponse response = bookingCalculatorService.calculatePrice(request);

        assertEquals(ROOM_ID, response.roomId());
        assertEquals(ROOM_TYPE_ID, response.roomTypeId());
        assertEquals(2, response.nights());
        assertEquals(money("2500.00"), response.roomsTotal());
        assertEquals(money("10.00"), response.roomTaxPercentSnapshot());
        assertEquals(money("250.00"), response.taxTotal());
        assertEquals(money("2750.00"), response.totalAmount());
        assertEquals("VND", response.currency());
        assertEquals(2, response.dailyRates().size());
    }

    @Test
    void calculatePriceUsesZeroTaxWhenSettingIsMissing() {
        Room room = createRoom(2, 2, 4);
        BookingPriceCalculationRequest request = createRequest(2, 0);
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.of(room));
        when(rateEngineService.calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_OUT_DATE))
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
                ROOM_ID,
                CHECK_IN_DATE,
                CHECK_IN_DATE,
                1,
                0
        );

        assertThrows(
                BusinessValidationException.class,
                () -> bookingCalculatorService.calculatePrice(request)
        );

        verify(roomRepository, never()).findByIdAndDeletedAtIsNull(ROOM_ID);
    }

    @Test
    void calculatePriceRejectsMissingRoom() {
        BookingPriceCalculationRequest request = createRequest(1, 0);
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> bookingCalculatorService.calculatePrice(request)
        );

        verify(rateEngineService, never()).calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_OUT_DATE);
    }

    @Test
    void calculatePriceRejectsGuestCountAboveOccupancy() {
        Room room = createRoom(2, 2, 2);
        BookingPriceCalculationRequest request = createRequest(2, 1);
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.of(room));

        assertThrows(
                BusinessValidationException.class,
                () -> bookingCalculatorService.calculatePrice(request)
        );

        verify(rateEngineService, never()).calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_OUT_DATE);
    }

    @Test
    void calculatePriceRejectsInvalidTaxPercent() {
        Room room = createRoom(2, 2, 4);
        BookingPriceCalculationRequest request = createRequest(1, 0);
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.of(room));
        when(rateEngineService.calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_OUT_DATE))
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
                ROOM_ID,
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                adults,
                children
        );
    }

    private Room createRoom(
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

        Room room = Room.builder()
                .roomNumber("A101")
                .roomType(roomType)
                .isActive(true)
                .build();
        room.setId(ROOM_ID);
        return room;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
