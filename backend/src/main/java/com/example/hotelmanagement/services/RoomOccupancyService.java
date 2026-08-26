package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.RoomBookingStatus;
import com.example.hotelmanagement.dto.room.RoomOccupancyResponse;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.RoomOccupancyProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoomOccupancyService {

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookingRoomRepository bookingRoomRepository;
    private final RoomRepository roomRepository;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final Clock clock;

    public RoomOccupancyService(
            BookingRoomRepository bookingRoomRepository,
            RoomRepository roomRepository,
            HotelSettingsRepository hotelSettingsRepository,
            Clock clock
    ) {
        this.bookingRoomRepository = bookingRoomRepository;
        this.roomRepository = roomRepository;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.ROOM_OCCUPANCY_READ)
    public List<RoomOccupancyResponse> getOccupancy(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? today() : requestedDate;
        Map<String, RoomBookingStatus> effectiveStatuses = bookingRoomRepository.findOccupancyOnDate(date).stream()
                .collect(Collectors.toMap(
                        RoomOccupancyProjection::getRoomNumber,
                        projection -> mapBookingStatus(projection.getBookingStatus(), projection.getBookingRoomStatus())
                ));
        return roomRepository.findActiveRoomNumbers().stream()
                .map(roomNumber -> new RoomOccupancyResponse(roomNumber, effectiveStatuses.get(roomNumber)))
                .toList();
    }

    private RoomBookingStatus mapBookingStatus(
            BookingStatus bookingStatus,
            BookingRoomStatus bookingRoomStatus
    ) {
        if (bookingStatus == BookingStatus.PENDING && bookingRoomStatus == BookingRoomStatus.RESERVED) {
            return RoomBookingStatus.HELD;
        }
        if (bookingStatus == BookingStatus.CONFIRMED && bookingRoomStatus == BookingRoomStatus.RESERVED) {
            return RoomBookingStatus.RESERVED;
        }
        if (bookingStatus == BookingStatus.CHECKED_IN && bookingRoomStatus == BookingRoomStatus.OCCUPIED) {
            return RoomBookingStatus.OCCUPIED;
        }
        throw new IllegalStateException(
                "Unexpected room occupancy projection: bookingStatus=" + bookingStatus
                        + ", bookingRoomStatus=" + bookingRoomStatus
        );
    }

    private LocalDate today() {
        return OffsetDateTime.now(clock).atZoneSameInstant(resolveHotelZone()).toLocalDate();
    }

    private ZoneId resolveHotelZone() {
        String configured = hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY);
        if (configured == null || configured.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(configured);
        } catch (DateTimeException exception) {
            return FALLBACK_ZONE;
        }
    }
}
