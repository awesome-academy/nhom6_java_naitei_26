package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.pricing.RateOverrideCreateRequest;
import com.example.hotelmanagement.dto.pricing.RateOverrideResponse;
import com.example.hotelmanagement.dto.pricing.RateOverrideUpdateRequest;
import com.example.hotelmanagement.dto.pricing.RoomTypeRateOverrideCreateRequest;
import com.example.hotelmanagement.entity.RateOverride;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.RateOverrideConflictException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RateOverrideRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateOverrideServiceTest {

    private static final Long RATE_OVERRIDE_ID = 1L;
    private static final Long ROOM_TYPE_ID = 20L;
    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 25);

    @Mock
    private RateOverrideRepository rateOverrideRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    private RateOverrideService rateOverrideService;

    @BeforeEach
    void setUp() {
        rateOverrideService = new RateOverrideService(
                rateOverrideRepository,
                roomTypeRepository,
                new RateOverrideWeekdayCodec(new ObjectMapper())
        );
    }

    @Test
    void getActiveRateOverridesMapsSortedWeekdays() {
        RoomType roomType = createRoomType();
        RateOverride rateOverride = createRateOverride(roomType, "[7, 6]", 5);
        when(rateOverrideRepository.findAllByIsActiveTrueOrderByStartDateAscPriorityDescIdAsc())
                .thenReturn(List.of(rateOverride));

        List<RateOverrideResponse> responses = rateOverrideService.getActiveRateOverrides();

        assertEquals(1, responses.size());
        assertEquals(List.of(6, 7), responses.getFirst().weekdays());
        assertEquals("DLX", responses.getFirst().roomTypeCode());
        assertEquals("Deluxe", responses.getFirst().roomTypeName());
    }

    @Test
    void createRateOverrideTargetsRoomTypeAndNormalizesWeekdays() {
        RoomType roomType = createRoomType();
        RateOverrideCreateRequest request = createRequest(ROOM_TYPE_ID, Set.of(7, 6), 5);
        when(roomTypeRepository.findByIdAndDeletedAtIsNull(ROOM_TYPE_ID))
                .thenReturn(Optional.of(roomType));
        when(rateOverrideRepository.findActiveConflicts(
                ROOM_TYPE_ID, START_DATE, END_DATE, 5, null
        )).thenReturn(List.of());
        when(rateOverrideRepository.save(any(RateOverride.class)))
                .thenAnswer(invocation -> {
                    RateOverride saved = invocation.getArgument(0);
                    saved.setId(RATE_OVERRIDE_ID);
                    return saved;
                });

        RateOverrideResponse response = rateOverrideService.createRateOverride(request);

        ArgumentCaptor<RateOverride> captor = ArgumentCaptor.forClass(RateOverride.class);
        verify(rateOverrideRepository).save(captor.capture());
        RateOverride saved = captor.getValue();
        assertEquals("Weekend summer", saved.getName());
        assertEquals("[6,7]", saved.getWeekdays());
        assertEquals(roomType, saved.getRoomType());
        assertTrue(saved.getIsActive());
        assertEquals(List.of(6, 7), response.weekdays());
    }

    @Test
    void createRoomTypeRateOverrideUsesNormalizedPublicCode() {
        RoomType roomType = createRoomType();
        RoomTypeRateOverrideCreateRequest request = new RoomTypeRateOverrideCreateRequest(
                " Public room type rule ",
                START_DATE,
                END_DATE,
                money("1250.00"),
                Set.of(6, 7),
                7
        );
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));
        when(rateOverrideRepository.findActiveConflicts(
                ROOM_TYPE_ID, START_DATE, END_DATE, 7, null
        )).thenReturn(List.of());
        when(rateOverrideRepository.save(any(RateOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RateOverrideResponse response = rateOverrideService.createRoomTypeRateOverride(
                " dlx ",
                request
        );

        assertEquals("DLX", response.roomTypeCode());
        assertEquals("Deluxe", response.roomTypeName());
        assertEquals("Public room type rule", response.name());
        assertEquals(List.of(6, 7), response.weekdays());
        verify(roomTypeRepository).findByCodeIgnoreCaseAndDeletedAtIsNull("DLX");
    }

    @Test
    void createRateOverrideRejectsMissingRoomType() {
        RateOverrideCreateRequest noTarget = createRequest(null, null, 1);
        assertThrows(
                BusinessValidationException.class,
                () -> rateOverrideService.createRateOverride(noTarget)
        );

        verifyNoInteractions(roomTypeRepository, rateOverrideRepository);
    }

    @Test
    void createRateOverrideRejectsInvalidDateRange() {
        RateOverrideCreateRequest request = new RateOverrideCreateRequest(
                ROOM_TYPE_ID,
                "Invalid dates",
                END_DATE,
                START_DATE,
                money("900.00"),
                null,
                1
        );

        assertThrows(
                BusinessValidationException.class,
                () -> rateOverrideService.createRateOverride(request)
        );

        verifyNoInteractions(roomTypeRepository, rateOverrideRepository);
    }

    @Test
    void createRateOverrideRejectsEmptyWeekdays() {
        RateOverrideCreateRequest request = createRequest(ROOM_TYPE_ID, Set.of(), 1);

        assertThrows(
                BusinessValidationException.class,
                () -> rateOverrideService.createRateOverride(request)
        );

        verifyNoInteractions(roomTypeRepository, rateOverrideRepository);
    }

    @Test
    void createRateOverrideRejectsMissingTarget() {
        RateOverrideCreateRequest request = createRequest(ROOM_TYPE_ID, null, 1);
        when(roomTypeRepository.findByIdAndDeletedAtIsNull(ROOM_TYPE_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> rateOverrideService.createRateOverride(request)
        );

        verify(rateOverrideRepository, never()).save(any(RateOverride.class));
    }

    @Test
    void createRateOverrideRejectsActualWeekdayConflict() {
        RoomType roomType = createRoomType();
        RateOverride existing = createRateOverride(roomType, "[6,7]", 5);
        RateOverrideCreateRequest request = createRequest(ROOM_TYPE_ID, Set.of(6), 5);
        when(roomTypeRepository.findByIdAndDeletedAtIsNull(ROOM_TYPE_ID))
                .thenReturn(Optional.of(roomType));
        when(rateOverrideRepository.findActiveConflicts(
                ROOM_TYPE_ID, START_DATE, END_DATE, 5, null
        )).thenReturn(List.of(existing));

        RateOverrideConflictException exception = assertThrows(
                RateOverrideConflictException.class,
                () -> rateOverrideService.createRateOverride(request)
        );

        assertEquals(RATE_OVERRIDE_ID, exception.getConflictingRateOverrideId());
        verify(rateOverrideRepository, never()).save(any(RateOverride.class));
    }

    @Test
    void createRateOverrideAllowsDisjointWeekdaysAtSamePriority() {
        RoomType roomType = createRoomType();
        RateOverride existing = createRateOverride(roomType, "[7]", 5);
        RateOverrideCreateRequest request = createRequest(ROOM_TYPE_ID, Set.of(6), 5);
        when(roomTypeRepository.findByIdAndDeletedAtIsNull(ROOM_TYPE_ID))
                .thenReturn(Optional.of(roomType));
        when(rateOverrideRepository.findActiveConflicts(
                ROOM_TYPE_ID, START_DATE, END_DATE, 5, null
        )).thenReturn(List.of(existing));
        when(rateOverrideRepository.save(any(RateOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RateOverrideResponse response = rateOverrideService.createRateOverride(request);

        assertEquals(List.of(6), response.weekdays());
        verify(rateOverrideRepository).save(any(RateOverride.class));
    }

    @Test
    void updateRateOverrideExcludesCurrentRecordFromConflictSearch() {
        RoomType roomType = createRoomType();
        RateOverride existing = createRateOverride(roomType, null, 2);
        RateOverrideUpdateRequest request = new RateOverrideUpdateRequest(
                ROOM_TYPE_ID,
                "Updated holiday",
                START_DATE,
                END_DATE,
                money("1500.00"),
                Set.of(7),
                8
        );
        when(rateOverrideRepository.findByIdAndIsActiveTrue(RATE_OVERRIDE_ID))
                .thenReturn(Optional.of(existing));
        when(roomTypeRepository.findByIdAndDeletedAtIsNull(ROOM_TYPE_ID))
                .thenReturn(Optional.of(roomType));
        when(rateOverrideRepository.findActiveConflicts(
                ROOM_TYPE_ID, START_DATE, END_DATE, 8, RATE_OVERRIDE_ID
        )).thenReturn(List.of());
        when(rateOverrideRepository.save(existing)).thenReturn(existing);

        RateOverrideResponse response = rateOverrideService.updateRateOverride(
                RATE_OVERRIDE_ID,
                request
        );

        assertEquals("Updated holiday", response.name());
        assertEquals(8, response.priority());
        assertEquals(List.of(7), response.weekdays());
    }

    @Test
    void deleteRateOverrideDeactivatesInsteadOfDeleting() {
        RateOverride existing = createRateOverride(createRoomType(), null, 2);
        when(rateOverrideRepository.findByIdAndIsActiveTrue(RATE_OVERRIDE_ID))
                .thenReturn(Optional.of(existing));

        rateOverrideService.deleteRateOverride(RATE_OVERRIDE_ID);

        assertFalse(existing.getIsActive());
        verify(rateOverrideRepository).save(existing);
        verify(rateOverrideRepository, never()).delete(any(RateOverride.class));
    }

    private RateOverrideCreateRequest createRequest(
            Long roomTypeId,
            Set<Integer> weekdays,
            int priority
    ) {
        return new RateOverrideCreateRequest(
                roomTypeId,
                " Weekend summer ",
                START_DATE,
                END_DATE,
                money("1200.00"),
                weekdays,
                priority
        );
    }

    private RateOverride createRateOverride(
            RoomType roomType,
            String weekdays,
            int priority
    ) {
        RateOverride rateOverride = RateOverride.builder()
                .roomType(roomType)
                .name("Existing rate")
                .startDate(START_DATE)
                .endDate(END_DATE)
                .price(money("1000.00"))
                .weekdays(weekdays)
                .priority(priority)
                .isActive(true)
                .build();
        rateOverride.setId(RATE_OVERRIDE_ID);
        return rateOverride;
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

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
