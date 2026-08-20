package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.AvailableRoomProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDate CHECK_OUT_DATE = LocalDate.of(2026, 9, 12);
    private static final Set<BookingRoomStatus> BLOCKING_STATUSES = Set.of(
            BookingRoomStatus.RESERVED,
            BookingRoomStatus.OCCUPIED
    );

    @Mock
    private RoomRepository roomRepository;

    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(roomRepository);
    }

    @Test
    void getAvailableRoomsGroupsOrderedRepositoryResultsByRoomType() {
        when(roomRepository.findAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                RoomOperationalStatus.ACTIVE,
                BLOCKING_STATUSES
        )).thenReturn(List.of(
                projection(1L, 101L),
                projection(1L, 102L),
                projection(2L, 201L)
        ));

        Map<Long, List<Long>> result = availabilityService.getAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE
        );

        assertEquals(List.of(1L, 2L), result.keySet().stream().toList());
        assertEquals(List.of(101L, 102L), result.get(1L));
        assertEquals(List.of(201L), result.get(2L));
    }

    @Test
    void getAvailableRoomsReturnsEmptyMapWhenNoRoomsMatch() {
        when(roomRepository.findAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                RoomOperationalStatus.ACTIVE,
                BLOCKING_STATUSES
        )).thenReturn(List.of());

        Map<Long, List<Long>> result = availabilityService.getAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE
        );

        assertEquals(Map.of(), result);
    }

    @Test
    void getAvailableRoomsRejectsMissingDateBeforeQuerying() {
        assertThrows(
                BusinessValidationException.class,
                () -> availabilityService.getAvailableRooms(null, CHECK_OUT_DATE)
        );
        assertThrows(
                BusinessValidationException.class,
                () -> availabilityService.getAvailableRooms(CHECK_IN_DATE, null)
        );

        verify(roomRepository, never()).findAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                RoomOperationalStatus.ACTIVE,
                BLOCKING_STATUSES
        );
    }

    @Test
    void getAvailableRoomsRejectsEqualOrReversedDatesBeforeQuerying() {
        assertThrows(
                BusinessValidationException.class,
                () -> availabilityService.getAvailableRooms(CHECK_IN_DATE, CHECK_IN_DATE)
        );
        assertThrows(
                BusinessValidationException.class,
                () -> availabilityService.getAvailableRooms(CHECK_OUT_DATE, CHECK_IN_DATE)
        );

        verify(roomRepository, never()).findAvailableRooms(
                CHECK_IN_DATE,
                CHECK_OUT_DATE,
                RoomOperationalStatus.ACTIVE,
                BLOCKING_STATUSES
        );
    }

    private AvailableRoomProjection projection(Long roomTypeId, Long roomId) {
        return new AvailableRoomProjection() {
            @Override
            public Long getRoomTypeId() {
                return roomTypeId;
            }

            @Override
            public Long getRoomId() {
                return roomId;
            }
        };
    }
}
