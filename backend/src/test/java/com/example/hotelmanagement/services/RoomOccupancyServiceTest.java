package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.RoomBookingStatus;
import com.example.hotelmanagement.dto.room.RoomOccupancyResponse;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.RoomOccupancyProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomOccupancyServiceTest {

    @Mock
    private BookingRoomRepository bookingRoomRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    private RoomOccupancyService roomOccupancyService;

    @BeforeEach
    void setUp() {
        roomOccupancyService = new RoomOccupancyService(
                bookingRoomRepository,
                roomRepository,
                hotelSettingsRepository,
                Clock.fixed(Instant.parse("2026-08-25T23:30:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void mapsEffectiveBookingStatesAndUsesHalfOpenDateAtRepositoryBoundary() {
        List<RoomOccupancyProjection> projections = List.of(
                projection("A101", BookingStatus.PENDING, BookingRoomStatus.RESERVED),
                projection("A102", BookingStatus.CONFIRMED, BookingRoomStatus.RESERVED),
                projection("A103", BookingStatus.CHECKED_IN, BookingRoomStatus.OCCUPIED)
        );
        when(bookingRoomRepository.findOccupancyOnDate(LocalDate.of(2026, 8, 25))).thenReturn(projections);
        when(roomRepository.findActiveRoomNumbers()).thenReturn(List.of("A101", "A102", "A103", "A104"));

        List<RoomOccupancyResponse> response = roomOccupancyService.getOccupancy(LocalDate.of(2026, 8, 25));

        assertEquals(List.of(
                new RoomOccupancyResponse("A101", RoomBookingStatus.HELD),
                new RoomOccupancyResponse("A102", RoomBookingStatus.RESERVED),
                new RoomOccupancyResponse("A103", RoomBookingStatus.OCCUPIED),
                new RoomOccupancyResponse("A104", null)
        ), response);
        verify(bookingRoomRepository).findOccupancyOnDate(LocalDate.of(2026, 8, 25));
    }

    @Test
    void defaultsToHotelLocalDateWhenDateIsMissing() {
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY))
                .thenReturn("Asia/Ho_Chi_Minh");
        when(bookingRoomRepository.findOccupancyOnDate(LocalDate.of(2026, 8, 26))).thenReturn(List.of());
        when(roomRepository.findActiveRoomNumbers()).thenReturn(List.of("A101"));

        roomOccupancyService.getOccupancy(null);

        verify(bookingRoomRepository).findOccupancyOnDate(LocalDate.of(2026, 8, 26));
    }

    private RoomOccupancyProjection projection(
            String roomNumber,
            BookingStatus bookingStatus,
            BookingRoomStatus bookingRoomStatus
    ) {
        RoomOccupancyProjection projection = org.mockito.Mockito.mock(RoomOccupancyProjection.class);
        when(projection.getRoomNumber()).thenReturn(roomNumber);
        when(projection.getBookingStatus()).thenReturn(bookingStatus);
        when(projection.getBookingRoomStatus()).thenReturn(bookingRoomStatus);
        return projection;
    }
}
