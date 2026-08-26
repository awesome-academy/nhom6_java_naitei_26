package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.RoomBookingMapResponse;
import com.example.hotelmanagement.dto.room.RoomTimelineEventResponse;
import com.example.hotelmanagement.dto.room.RoomTimelineEventType;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomStatusBlock;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.RoomBookingTimelineProjection;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RoomBookingMapService {

    private final RoomRepository roomRepository;
    private final RoomStatusBlockRepository roomStatusBlockRepository;
    private final BookingRoomRepository bookingRoomRepository;

    public RoomBookingMapService(
            RoomRepository roomRepository,
            RoomStatusBlockRepository roomStatusBlockRepository,
            BookingRoomRepository bookingRoomRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomStatusBlockRepository = roomStatusBlockRepository;
        this.bookingRoomRepository = bookingRoomRepository;
    }

    @PreAuthorize(PermissionExpressions.ROOM_BOOKING_MAP_READ)
    public List<RoomBookingMapResponse> getBookingMap(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        Map<Long, List<RoomTimelineEventResponse>> eventsByRoom = new HashMap<>();
        for (RoomBookingTimelineProjection booking : bookingRoomRepository.findBookingTimeline(startDate, endDate)) {
            eventsByRoom.computeIfAbsent(booking.getRoomId(), ignored -> new ArrayList<>())
                    .add(new RoomTimelineEventResponse(
                            RoomTimelineEventType.BOOKING,
                            booking.getStartDate(),
                            booking.getEndDate(),
                            booking.getBookingCode(),
                            booking.getBookingPublicId(),
                            booking.getBookingCode(),
                            booking.getBookingStatus(),
                            booking.getBookingRoomStatus(),
                            null,
                            null
                    ));
        }

        for (RoomStatusBlock block : roomStatusBlockRepository.findOverlappingDateRange(startDate, endDate)) {
            eventsByRoom.computeIfAbsent(block.getRoom().getId(), ignored -> new ArrayList<>())
                    .add(new RoomTimelineEventResponse(
                            RoomTimelineEventType.ROOM_STATUS_BLOCK,
                            block.getStartDate(),
                            block.getEndDate(),
                            block.getBlockType().name(),
                            null,
                            null,
                            null,
                            null,
                            block.getBlockType(),
                            block.getReason()
                    ));
        }

        return roomRepository.findAllForBookingMap().stream()
                .map(room -> mapRoom(room, eventsByRoom.getOrDefault(room.getId(), List.of())))
                .toList();
    }

    private RoomBookingMapResponse mapRoom(Room room, List<RoomTimelineEventResponse> events) {
        List<RoomTimelineEventResponse> sortedEvents = events.stream()
                .sorted(Comparator.comparing(RoomTimelineEventResponse::startDate)
                        .thenComparing(RoomTimelineEventResponse::type))
                .toList();

        String unavailableReason = null;
        if (room.getHousekeepingStatus() != HousekeepingStatus.CLEAN) {
            unavailableReason = "Phòng chưa ở trạng thái sạch";
        } else if (room.getOperationalStatus() != RoomOperationalStatus.ACTIVE) {
            unavailableReason = "Phòng không ở trạng thái hoạt động";
        } else if (room.getRoomType() == null
                || !Boolean.TRUE.equals(room.getRoomType().getIsActive())
                || room.getRoomType().getDeletedAt() != null) {
            unavailableReason = "Loại phòng không còn kinh doanh";
        } else if (events.stream().anyMatch(event -> event.type() == RoomTimelineEventType.BOOKING)) {
            unavailableReason = "Phòng đã có booking trong khoảng ngày này";
        } else if (events.stream().anyMatch(event -> event.type() == RoomTimelineEventType.ROOM_STATUS_BLOCK)) {
            unavailableReason = "Phòng có lịch bảo trì trong khoảng ngày này";
        }

        Integer maxOccupancy = room.getMaxOccupancyOverride() != null
                ? room.getMaxOccupancyOverride()
                : room.getRoomType() == null ? null : room.getRoomType().getMaxOccupancy();

        return new RoomBookingMapResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType() == null ? null : room.getRoomType().getCode(),
                room.getRoomType() == null ? null : room.getRoomType().getName(),
                room.getViewType(),
                room.getFloor(),
                room.getOperationalStatus(),
                room.getHousekeepingStatus(),
                maxOccupancy,
                unavailableReason == null,
                unavailableReason,
                sortedEvents
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessValidationException("Check-in date and check-out date are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BusinessValidationException("Check-out date must be after check-in date");
        }
    }
}
