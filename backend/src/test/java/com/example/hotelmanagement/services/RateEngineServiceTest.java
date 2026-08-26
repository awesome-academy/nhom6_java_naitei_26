package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.RateOverride;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.PricingConfigurationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RateOverrideRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class RateEngineServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ROOM_TYPE_ID = 20L;
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDate CHECK_OUT_DATE = LocalDate.of(2026, 8, 25);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RateOverrideRepository rateOverrideRepository;

    private RateEngineService rateEngineService;

    @BeforeEach
    void setUp() {
        rateEngineService = new RateEngineService(
                roomRepository,
                rateOverrideRepository,
                new RateOverrideWeekdayCodec(new ObjectMapper())
        );
    }

    @Test
    void calculateDailyRatesReturnsBasePriceForEachNight() {
        Room room = createRoom(null);
        LocalDate checkOutDate = LocalDate.of(2026, 8, 23);
        stubPricingData(room, checkOutDate, List.of());

        List<DailyRateResponse> rates = rateEngineService.calculateDailyRates(
                ROOM_ID,
                CHECK_IN_DATE,
                checkOutDate
        );

        assertEquals(List.of(
                new DailyRateResponse(LocalDate.of(2026, 8, 21), money("1000.00")),
                new DailyRateResponse(LocalDate.of(2026, 8, 22), money("1000.00"))
        ), rates);
        verify(rateOverrideRepository).findActiveRoomTypeOverridesForPricing(
                ROOM_TYPE_ID,
                CHECK_IN_DATE,
                checkOutDate
        );
    }

    @Test
    void calculateDailyRatesUsesRoomPriceWhenNoRateOverrideMatches() {
        Room room = createRoom(money("1200.00"));
        LocalDate checkOutDate = LocalDate.of(2026, 8, 22);
        stubPricingData(room, checkOutDate, List.of());

        List<DailyRateResponse> rates = rateEngineService.calculateDailyRates(
                ROOM_ID,
                CHECK_IN_DATE,
                checkOutDate
        );

        assertEquals(money("1200.00"), rates.getFirst().price());
    }

    @Test
    void calculateDailyRatesUsesRateOverrideBeforeRoomPrice() {
        Room room = createRoom(money("1200.00"));
        LocalDate checkOutDate = LocalDate.of(2026, 8, 22);
        RateOverride rateOverride = createRoomTypeOverride(
                1L, 1, money("900.00"), CHECK_IN_DATE, CHECK_OUT_DATE, null
        );
        stubPricingData(room, checkOutDate, List.of(rateOverride));

        List<DailyRateResponse> rates = rateEngineService.calculateDailyRates(
                ROOM_ID,
                CHECK_IN_DATE,
                checkOutDate
        );

        assertEquals(money("900.00"), rates.getFirst().price());
    }

    @Test
    void calculateDailyRatesSelectsHighestPriorityOverride() {
        Room room = createRoom(null);
        LocalDate checkOutDate = LocalDate.of(2026, 8, 22);
        RateOverride lowPriority = createRoomTypeOverride(
                1L, 5, money("900.00"), CHECK_IN_DATE, CHECK_OUT_DATE, null
        );
        RateOverride highPriority = createRoomTypeOverride(
                2L, 10, money("800.00"), CHECK_IN_DATE, CHECK_OUT_DATE, null
        );
        stubPricingData(room, checkOutDate, List.of(lowPriority, highPriority));

        List<DailyRateResponse> rates = rateEngineService.calculateDailyRates(
                ROOM_ID,
                CHECK_IN_DATE,
                checkOutDate
        );

        assertEquals(money("800.00"), rates.getFirst().price());
    }

    @Test
    void calculateDailyRatesUsesIsoWeekdaysForSaturdayAndSunday() {
        Room room = createRoom(null);
        RateOverride weekendRate = createRoomTypeOverride(
                1L,
                5,
                money("1500.00"),
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                "[6, 7]"
        );
        stubPricingData(room, CHECK_OUT_DATE, List.of(weekendRate));

        List<DailyRateResponse> rates = rateEngineService.calculateDailyRates(
                ROOM_ID,
                CHECK_IN_DATE,
                CHECK_OUT_DATE
        );

        assertEquals(List.of(
                new DailyRateResponse(LocalDate.of(2026, 8, 21), money("1000.00")),
                new DailyRateResponse(LocalDate.of(2026, 8, 22), money("1500.00")),
                new DailyRateResponse(LocalDate.of(2026, 8, 23), money("1500.00")),
                new DailyRateResponse(LocalDate.of(2026, 8, 24), money("1000.00"))
        ), rates);
    }

    @Test
    void calculateDailyRatesRejectsEqualPriorityOverridesForSameRoomType() {
        Room room = createRoom(null);
        LocalDate checkOutDate = LocalDate.of(2026, 8, 22);
        RateOverride roomTypeRate = createRoomTypeOverride(
                1L, 5, money("900.00"), CHECK_IN_DATE, CHECK_OUT_DATE, null
        );
        RateOverride secondRoomTypeRate = createRoomTypeOverride(
                2L, 5, money("850.00"), CHECK_IN_DATE, CHECK_OUT_DATE, null
        );
        stubPricingData(room, checkOutDate, List.of(roomTypeRate, secondRoomTypeRate));

        assertThrows(
                PricingConfigurationException.class,
                () -> rateEngineService.calculateDailyRates(
                        ROOM_ID,
                        CHECK_IN_DATE,
                        checkOutDate
                )
        );
    }

    @Test
    void calculateDailyRatesRejectsMalformedWeekdays() {
        Room room = createRoom(null);
        LocalDate checkOutDate = LocalDate.of(2026, 8, 22);
        RateOverride malformedRate = createRoomTypeOverride(
                1L, 5, money("900.00"), CHECK_IN_DATE, CHECK_OUT_DATE, "[6, invalid]"
        );
        stubPricingData(room, checkOutDate, List.of(malformedRate));

        assertThrows(
                PricingConfigurationException.class,
                () -> rateEngineService.calculateDailyRates(
                        ROOM_ID,
                        CHECK_IN_DATE,
                        checkOutDate
                )
        );
    }

    @Test
    void calculateDailyRatesRejectsUnsupportedWeekdayNumber() {
        Room room = createRoom(null);
        LocalDate checkOutDate = LocalDate.of(2026, 8, 22);
        RateOverride malformedRate = createRoomTypeOverride(
                1L, 5, money("900.00"), CHECK_IN_DATE, CHECK_OUT_DATE, "[0, 7]"
        );
        stubPricingData(room, checkOutDate, List.of(malformedRate));

        assertThrows(
                PricingConfigurationException.class,
                () -> rateEngineService.calculateDailyRates(
                        ROOM_ID,
                        CHECK_IN_DATE,
                        checkOutDate
                )
        );
    }

    @Test
    void calculateDailyRatesRejectsInvalidStayDatesBeforeLoadingRoom() {
        assertThrows(
                BusinessValidationException.class,
                () -> rateEngineService.calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_IN_DATE)
        );

        verify(roomRepository, never()).findByIdAndDeletedAtIsNull(ROOM_ID);
    }

    @Test
    void calculateDailyRatesRejectsMissingRoom() {
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> rateEngineService.calculateDailyRates(ROOM_ID, CHECK_IN_DATE, CHECK_OUT_DATE)
        );

        verify(rateOverrideRepository, never()).findActiveRoomTypeOverridesForPricing(
                ROOM_TYPE_ID,
                CHECK_IN_DATE,
                CHECK_OUT_DATE
        );
    }

    private void stubPricingData(
            Room room,
            LocalDate checkOutDate,
            List<RateOverride> rateOverrides
    ) {
        when(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).thenReturn(Optional.of(room));
        when(rateOverrideRepository.findActiveRoomTypeOverridesForPricing(
                ROOM_TYPE_ID,
                CHECK_IN_DATE,
                checkOutDate
        )).thenReturn(rateOverrides);
    }

    private Room createRoom(BigDecimal priceOverride) {
        RoomType roomType = createRoomType();

        Room room = Room.builder()
                .roomNumber("A101")
                .roomType(roomType)
                .priceOverride(priceOverride)
                .isActive(true)
                .build();
        room.setId(ROOM_ID);
        return room;
    }

    private RoomType createRoomType() {
        RoomType roomType = RoomType.builder()
                .code("DLX")
                .name("Deluxe")
                .slug("deluxe")
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(0)
                .basePrice(money("1000.00"))
                .currency("VND")
                .isActive(true)
                .build();
        roomType.setId(ROOM_TYPE_ID);
        return roomType;
    }

    private RateOverride createRoomTypeOverride(
            Long id,
            int priority,
            BigDecimal price,
            LocalDate startDate,
            LocalDate endDate,
            String weekdays
    ) {
        RateOverride rateOverride = RateOverride.builder()
                .roomType(createRoomType())
                .name("Room type rate " + id)
                .startDate(startDate)
                .endDate(endDate)
                .price(price)
                .weekdays(weekdays)
                .priority(priority)
                .isActive(true)
                .build();
        rateOverride.setId(id);
        return rateOverride;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
