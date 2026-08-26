package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.RoomBookingMapResponse;
import com.example.hotelmanagement.dto.room.RoomTimelineEventType;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomStatusBlock;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomBlockType;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.RoomBookingTimelineProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomBookingMapServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomStatusBlockRepository roomStatusBlockRepository;
    @Mock
    private BookingRoomRepository bookingRoomRepository;

    private RoomBookingMapService service;

    @BeforeEach
    void setUp() {
        service = new RoomBookingMapService(
                roomRepository,
                roomStatusBlockRepository,
                bookingRoomRepository
        );
    }

    @Test
    void marksCleanRoomWithoutEventsAsSelectable() {
        Room room = room(1L, "A101", HousekeepingStatus.CLEAN);
        when(roomRepository.findAllForBookingMap()).thenReturn(List.of(room));
        when(bookingRoomRepository.findBookingTimeline(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)
        )).thenReturn(List.of());
        when(roomStatusBlockRepository.findOverlappingDateRange(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)
        )).thenReturn(List.of());

        RoomBookingMapResponse response = service.getBookingMap(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)
        ).getFirst();

        assertThat(response.selectable()).isTrue();
        assertThat(response.timeline()).isEmpty();
    }

    @Test
    void blocksRoomWithBookingAndMaintenanceEvents() {
        Room room = room(1L, "A101", HousekeepingStatus.CLEAN);
        RoomStatusBlock block = RoomStatusBlock.builder()
                .room(room)
                .blockType(RoomBlockType.MAINTENANCE)
                .startDate(LocalDate.of(2026, 9, 4))
                .endDate(LocalDate.of(2026, 9, 6))
                .reason("Điều hòa")
                .build();
        RoomBookingTimelineProjection booking = mock(RoomBookingTimelineProjection.class);
        when(booking.getRoomId()).thenReturn(1L);
        when(booking.getBookingPublicId()).thenReturn("booking-public-id");
        when(booking.getBookingCode()).thenReturn("BK-2026-000001");
        when(booking.getBookingStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getBookingRoomStatus()).thenReturn(BookingRoomStatus.RESERVED);
        when(booking.getStartDate()).thenReturn(LocalDate.of(2026, 9, 2));
        when(booking.getEndDate()).thenReturn(LocalDate.of(2026, 9, 4));
        when(roomRepository.findAllForBookingMap()).thenReturn(List.of(room));
        when(bookingRoomRepository.findBookingTimeline(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7)
        )).thenReturn(List.of(booking));
        when(roomStatusBlockRepository.findOverlappingDateRange(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7)
        )).thenReturn(List.of(block));

        RoomBookingMapResponse response = service.getBookingMap(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7)
        ).getFirst();

        assertThat(response.selectable()).isFalse();
        assertThat(response.unavailableReason()).contains("booking");
        assertThat(response.timeline()).extracting(event -> event.type())
                .containsExactlyInAnyOrder(RoomTimelineEventType.BOOKING, RoomTimelineEventType.ROOM_STATUS_BLOCK);
    }

    @Test
    void rejectsClosedDateRange() {
        assertThatThrownBy(() -> service.getBookingMap(
                LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3)
        )).isInstanceOf(com.example.hotelmanagement.exceptions.BusinessValidationException.class);
    }

    private Room room(Long id, String number, HousekeepingStatus housekeepingStatus) {
        RoomType roomType = RoomType.builder()
                .code("DLX")
                .name("Deluxe")
                .slug("deluxe")
                .bedCount(2)
                .maxOccupancy(3)
                .maxAdults(3)
                .maxChildren(0)
                .basePrice(new BigDecimal("1500000"))
                .isActive(true)
                .build();
        Room room = Room.builder()
                .roomType(roomType)
                .roomNumber(number)
                .housekeepingStatus(housekeepingStatus)
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .isActive(true)
                .build();
        room.setId(id);
        return room;
    }
}
