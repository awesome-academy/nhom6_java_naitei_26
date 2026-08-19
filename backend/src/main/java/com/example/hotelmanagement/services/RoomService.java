package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.HousekeepingStatusUpdateRequest;
import com.example.hotelmanagement.dto.room.RoomCreateRequest;
import com.example.hotelmanagement.dto.room.RoomResponse;
import com.example.hotelmanagement.dto.room.RoomUpdateRequest;
import com.example.hotelmanagement.dto.roomtype.AmenityResponse;
import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.AmenityRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomSpecifications;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Validated
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final AmenityRepository amenityRepository;
    private final RoomImageService roomImageService;
    private final Clock clock;

    public RoomService(
            RoomRepository roomRepository,
            RoomTypeRepository roomTypeRepository,
            AmenityRepository amenityRepository,
            RoomImageService roomImageService,
            Clock clock
    ) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.amenityRepository = amenityRepository;
        this.roomImageService = roomImageService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public List<RoomResponse> getRooms(
            String roomTypeCode,
            RoomView viewType,
            Integer floor,
            Collection<String> amenityCodes
    ) {
        String normalizedRoomTypeCode = normalizeOptionalCode(roomTypeCode);
        Set<String> normalizedAmenityCodes = normalizeAmenityCodes(amenityCodes);
        validateFilterableAmenities(normalizedAmenityCodes);

        Sort sort = Sort.by(
                Sort.Order.asc("floor"),
                Sort.Order.asc("roomNumber")
        );
        return roomRepository.findAll(
                        RoomSpecifications.matchesFilters(
                                normalizedRoomTypeCode,
                                viewType,
                                floor,
                                normalizedAmenityCodes
                        ),
                        sort
                )
                .stream()
                .map(this::mapRoomResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public RoomResponse getRoom(String roomNumber) {
        return mapRoomResponse(getExistingRoom(roomNumber));
    }

    @PreAuthorize(PermissionExpressions.ROOM_CREATE)
    public RoomResponse createRoom(@Valid RoomCreateRequest request, Long createdBy) {
        String normalizedRoomNumber = normalizeRoomNumber(request.roomNumber());
        if (roomRepository.existsByRoomNumberIgnoreCaseAndDeletedAtIsNull(normalizedRoomNumber)) {
            throw new DuplicateResourceException("Room", "room number", normalizedRoomNumber);
        }

        RoomType roomType = getAssignableRoomType(request.roomTypeCode());
        Room room = Room.builder()
                .roomNumber(normalizedRoomNumber)
                .roomType(roomType)
                .viewType(request.viewType() == null ? RoomView.NONE : request.viewType())
                .floor(request.floor())
                .priceOverride(request.priceOverride())
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .isActive(true)
                .createdBy(createdBy)
                .build();

        return mapRoomResponse(roomRepository.saveAndFlush(room));
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomResponse updateRoom(String roomNumber, @Valid RoomUpdateRequest request) {
        Room room = getExistingRoom(roomNumber);
        String normalizedTypeCode = normalizeCode(request.roomTypeCode(), "Room type code");
        if (!room.getRoomType().getCode().equalsIgnoreCase(normalizedTypeCode)) {
            room.setRoomType(getAssignableRoomType(normalizedTypeCode));
        }

        room.setViewType(request.viewType());
        room.setFloor(request.floor());
        room.setPriceOverride(request.priceOverride());
        return mapRoomResponse(roomRepository.saveAndFlush(room));
    }

    @PreAuthorize(PermissionExpressions.ROOM_DELETE)
    public void deleteRoom(String roomNumber) {
        Room room = getExistingRoom(roomNumber);
        room.setIsActive(false);
        room.setDeletedAt(OffsetDateTime.now(clock));
        roomRepository.saveAndFlush(room);
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomResponse updateHousekeepingStatus(
            String roomNumber,
            @Valid HousekeepingStatusUpdateRequest request
    ) {
        Room room = getExistingRoom(roomNumber);
        HousekeepingStatus currentStatus = room.getHousekeepingStatus();
        HousekeepingStatus requestedStatus = request.status();
        if (currentStatus == requestedStatus) {
            return mapRoomResponse(room);
        }
        if (!isAllowedHousekeepingTransition(currentStatus, requestedStatus)) {
            throw new BusinessValidationException(
                    "Invalid housekeeping transition: " + currentStatus + " -> " + requestedStatus
            );
        }

        room.setHousekeepingStatus(requestedStatus);
        return mapRoomResponse(roomRepository.saveAndFlush(room));
    }

    private boolean isAllowedHousekeepingTransition(
            HousekeepingStatus currentStatus,
            HousekeepingStatus requestedStatus
    ) {
        return switch (currentStatus) {
            case CLEAN -> requestedStatus == HousekeepingStatus.DIRTY;
            case DIRTY -> requestedStatus == HousekeepingStatus.CLEANING;
            case CLEANING -> requestedStatus == HousekeepingStatus.CLEAN;
            case INSPECTED -> false;
        };
    }

    private Room getExistingRoom(String roomNumber) {
        String normalizedRoomNumber = normalizeRoomNumber(roomNumber);
        return roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull(normalizedRoomNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Room", normalizedRoomNumber));
    }

    private RoomType getAssignableRoomType(String code) {
        String normalizedCode = normalizeCode(code, "Room type code");
        RoomType roomType = roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", normalizedCode));
        if (!Boolean.TRUE.equals(roomType.getIsActive())) {
            throw new BusinessValidationException("Only active room types can be assigned to a room");
        }
        return roomType;
    }

    private void validateFilterableAmenities(Set<String> amenityCodes) {
        if (amenityCodes.isEmpty()) {
            return;
        }
        List<Amenity> amenities = amenityRepository.findAllByCodeIn(amenityCodes);
        Set<String> foundCodes = amenities.stream()
                .map(Amenity::getCode)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> invalidCodes = new LinkedHashSet<>(amenityCodes);
        invalidCodes.removeAll(foundCodes);
        amenities.stream()
                .filter(amenity -> !Boolean.TRUE.equals(amenity.getIsFilterable()))
                .map(Amenity::getCode)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .forEach(invalidCodes::add);
        if (!invalidCodes.isEmpty()) {
            throw new BusinessValidationException(
                    "Amenities are unknown or not filterable: " + String.join(", ", invalidCodes)
            );
        }
    }

    private Set<String> normalizeAmenityCodes(Collection<String> amenityCodes) {
        if (amenityCodes == null || amenityCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String code : amenityCodes) {
            normalizedCodes.add(normalizeCode(code, "Amenity code"));
        }
        return normalizedCodes;
    }

    private String normalizeOptionalCode(String code) {
        return code == null ? null : normalizeCode(code, "Room type code");
    }

    private String normalizeRoomNumber(String roomNumber) {
        return normalizeCode(roomNumber, "Room number");
    }

    private String normalizeCode(String code, String fieldName) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private RoomResponse mapRoomResponse(Room room) {
        Set<Amenity> effectiveAmenities = new LinkedHashSet<>(room.getRoomType().getAmenities());
        effectiveAmenities.addAll(room.getAmenities());
        List<AmenityResponse> amenities = effectiveAmenities.stream()
                .sorted(Comparator.comparing(Amenity::getSortOrder).thenComparing(Amenity::getCode))
                .map(this::mapAmenityResponse)
                .toList();

        return new RoomResponse(
                room.getRoomNumber(),
                room.getRoomType().getCode(),
                room.getRoomType().getName(),
                room.getViewType(),
                room.getFloor(),
                room.getOperationalStatus(),
                room.getHousekeepingStatus(),
                room.getPriceOverride(),
                room.getIsActive(),
                amenities,
                roomImageService.getRoomImageResponses(room),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }

    private AmenityResponse mapAmenityResponse(Amenity amenity) {
        return new AmenityResponse(
                amenity.getCode(),
                amenity.getName(),
                amenity.getIcon(),
                amenity.getCategory(),
                amenity.getIsFilterable(),
                amenity.getSortOrder()
        );
    }
}
