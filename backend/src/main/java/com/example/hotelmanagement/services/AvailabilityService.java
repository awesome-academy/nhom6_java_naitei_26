package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.AvailableRoomProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private static final Set<BookingRoomStatus> BLOCKING_BOOKING_STATUSES = Set.of(
            BookingRoomStatus.RESERVED,
            BookingRoomStatus.OCCUPIED
    );

    private final RoomRepository roomRepository;

    public AvailabilityService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @PreAuthorize(PermissionExpressions.BOOKING_CREATE)
    public Map<Long, List<Long>> getAvailableRooms(
            LocalDate checkInDate,
            LocalDate checkOutDate
    ) {
        validateStayPeriod(checkInDate, checkOutDate);

        List<AvailableRoomProjection> availableRooms = roomRepository.findAvailableRooms(
                checkInDate,
                checkOutDate,
                RoomOperationalStatus.ACTIVE,
                BLOCKING_BOOKING_STATUSES
        );
        Map<Long, List<Long>> availableRoomsByType = new LinkedHashMap<>();
        for (AvailableRoomProjection availableRoom : availableRooms) {
            availableRoomsByType.computeIfAbsent(
                    availableRoom.getRoomTypeId(),
                    ignored -> new ArrayList<>()
            ).add(availableRoom.getRoomId());
        }
        return availableRoomsByType;
    }

    private void validateStayPeriod(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null || checkOutDate == null) {
            throw new BusinessValidationException("Check-in date and check-out date are required");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new BusinessValidationException("Check-out date must be after check-in date");
        }
    }
}
