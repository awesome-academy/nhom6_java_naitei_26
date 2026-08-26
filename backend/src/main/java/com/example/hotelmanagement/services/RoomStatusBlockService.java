package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.RoomOperationalStatusResponse;
import com.example.hotelmanagement.dto.room.RoomOperationalStatusUpdateRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockCreateRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockExtendRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockResponse;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomStatusBlock;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.RoomStatusConflictException;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Validated
@Transactional
public class RoomStatusBlockService {

    private static final Logger log = LoggerFactory.getLogger(RoomStatusBlockService.class);
    private static final Set<BookingRoomStatus> EFFECTIVE_BOOKING_STATUSES = Set.of(
            BookingRoomStatus.RESERVED,
            BookingRoomStatus.OCCUPIED
    );

    private final RoomStatusBlockRepository roomStatusBlockRepository;
    private final RoomRepository roomRepository;
    private final BookingRoomRepository bookingRoomRepository;

    public RoomStatusBlockService(
            RoomStatusBlockRepository roomStatusBlockRepository,
            RoomRepository roomRepository,
            BookingRoomRepository bookingRoomRepository
    ) {
        this.roomStatusBlockRepository = roomStatusBlockRepository;
        this.roomRepository = roomRepository;
        this.bookingRoomRepository = bookingRoomRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public List<RoomStatusBlockResponse> getBlocks(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return roomStatusBlockRepository.findOverlappingDateRange(startDate, endDate)
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    @PreAuthorize(PermissionExpressions.MAINTENANCE_MANAGE)
    public RoomStatusBlockResponse createBlock(
            @Valid RoomStatusBlockCreateRequest request,
            Long createdBy
    ) {
        validateDateRange(request.startDate(), request.endDate());
        Room room = getActiveRoomForUpdate(request.roomNumber());
        validateNoConflict(room, request.startDate(), request.endDate(), null);

        RoomStatusBlock block = RoomStatusBlock.builder()
                .publicId(UUID.randomUUID().toString())
                .room(room)
                .blockType(request.blockType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .reason(normalizeOptionalText(request.reason()))
                .createdBy(createdBy)
                .build();
        try {
            return mapResponse(roomStatusBlockRepository.saveAndFlush(block));
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Database rejected room status block roomNumber={} publicId={}",
                    room.getRoomNumber(),
                    block.getPublicId(),
                    exception
            );
            throw new RoomStatusConflictException(
                    "Room status block conflicts with an existing block or booking",
                    exception
            );
        }
    }

    @PreAuthorize(PermissionExpressions.MAINTENANCE_MANAGE)
    public RoomStatusBlockResponse extendBlock(
            UUID publicId,
            @Valid RoomStatusBlockExtendRequest request
    ) {
        RoomStatusBlock block = getExistingBlock(publicId);
        Room room = getActiveRoomForUpdate(block.getRoom().getRoomNumber());
        if (!request.newEndDate().isAfter(block.getEndDate())) {
            throw new BusinessValidationException("New end date must be after the current end date");
        }
        validateNoConflict(room, block.getStartDate(), request.newEndDate(), block.getId());
        block.setEndDate(request.newEndDate());

        try {
            return mapResponse(roomStatusBlockRepository.saveAndFlush(block));
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Database rejected room status block extension roomNumber={} publicId={}",
                    room.getRoomNumber(),
                    publicId,
                    exception
            );
            throw new RoomStatusConflictException(
                    "Room status block conflicts with an existing block or booking",
                    exception
            );
        }
    }

    @PreAuthorize(PermissionExpressions.MAINTENANCE_MANAGE)
    public void deleteBlock(UUID publicId) {
        RoomStatusBlock block = getExistingBlock(publicId);
        getActiveRoomForUpdate(block.getRoom().getRoomNumber());
        roomStatusBlockRepository.delete(block);
        roomStatusBlockRepository.flush();
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomOperationalStatusResponse updateOperationalStatus(
            String roomNumber,
            @Valid RoomOperationalStatusUpdateRequest request
    ) {
        Room room = getActiveRoomForUpdate(roomNumber);
        RoomOperationalStatus requestedStatus = request.status();
        if (room.getOperationalStatus() == requestedStatus) {
            return mapOperationalStatus(room);
        }
        if (room.getOperationalStatus() == RoomOperationalStatus.ACTIVE
                && requestedStatus != RoomOperationalStatus.ACTIVE
                && bookingRoomRepository.existsByRoomIdAndStatusIn(
                        room.getId(),
                        EFFECTIVE_BOOKING_STATUSES
                )) {
            throw new RoomStatusConflictException(
                    "Room has an active booking and cannot be moved out of ACTIVE status"
            );
        }

        room.setOperationalStatus(requestedStatus);
        return mapOperationalStatus(roomRepository.saveAndFlush(room));
    }

    private RoomStatusBlock getExistingBlock(UUID publicId) {
        return roomStatusBlockRepository.findByPublicId(publicId.toString())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room status block",
                        publicId.toString()
                ));
    }

    private Room getActiveRoomForUpdate(String roomNumber) {
        String normalizedRoomNumber = normalizeRoomNumber(roomNumber);
        Room room = roomRepository.findOperationalForUpdateByRoomNumber(normalizedRoomNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Room", normalizedRoomNumber));
        if (!Boolean.TRUE.equals(room.getIsActive())) {
            throw new ResourceNotFoundException("Room", normalizedRoomNumber);
        }
        return room;
    }

    private void validateNoConflict(
            Room room,
            LocalDate startDate,
            LocalDate endDate,
            Long excludedBlockId
    ) {
        boolean overlapsBlock = excludedBlockId == null
                ? roomStatusBlockRepository.existsOverlappingBlock(room.getId(), startDate, endDate)
                : roomStatusBlockRepository.existsOverlappingBlockExcludingId(
                        room.getId(),
                        excludedBlockId,
                        startDate,
                        endDate
                );
        if (overlapsBlock) {
            throw new RoomStatusConflictException("Room status block overlaps an existing block");
        }
        if (bookingRoomRepository.existsOverlappingBooking(
                room.getId(),
                EFFECTIVE_BOOKING_STATUSES,
                startDate,
                endDate
        )) {
            throw new RoomStatusConflictException("Room status block overlaps an active booking");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessValidationException("Start date and end date are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BusinessValidationException("End date must be after start date");
        }
    }

    private String normalizeRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new BusinessValidationException("Room number cannot be blank");
        }
        return roomNumber.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private RoomStatusBlockResponse mapResponse(RoomStatusBlock block) {
        return new RoomStatusBlockResponse(
                block.getPublicId(),
                block.getRoom().getRoomNumber(),
                block.getRoom().getOperationalStatus(),
                block.getBlockType(),
                block.getStartDate(),
                block.getEndDate(),
                block.getReason(),
                block.getCreatedAt(),
                block.getUpdatedAt()
        );
    }

    private RoomOperationalStatusResponse mapOperationalStatus(Room room) {
        return new RoomOperationalStatusResponse(
                room.getRoomNumber(),
                room.getOperationalStatus()
        );
    }
}
