package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.pricing.RateOverrideCreateRequest;
import com.example.hotelmanagement.dto.pricing.RateOverrideResponse;
import com.example.hotelmanagement.dto.pricing.RateOverrideUpdateRequest;
import com.example.hotelmanagement.dto.pricing.RoomTypeRateOverrideCreateRequest;
import com.example.hotelmanagement.entity.RateOverride;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.RateOverrideConflictException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RateOverrideRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Validated
@Transactional
@PreAuthorize(PermissionExpressions.PRICING_MANAGE)
public class RateOverrideService {

    private final RateOverrideRepository rateOverrideRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final RateOverrideWeekdayCodec weekdayCodec;

    public RateOverrideService(
            RateOverrideRepository rateOverrideRepository,
            RoomTypeRepository roomTypeRepository,
            RoomRepository roomRepository,
            RateOverrideWeekdayCodec weekdayCodec
    ) {
        this.rateOverrideRepository = rateOverrideRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.weekdayCodec = weekdayCodec;
    }

    @Transactional(readOnly = true)
    public List<RateOverrideResponse> getActiveRateOverrides() {
        return rateOverrideRepository.findAllByIsActiveTrueOrderByStartDateAscPriorityDescIdAsc()
                .stream()
                .map(this::mapRateOverrideResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RateOverrideResponse getRateOverride(Long id) {
        return mapRateOverrideResponse(getExistingRateOverride(id));
    }

    public RateOverrideResponse createRateOverride(@Valid RateOverrideCreateRequest request) {
        validateRateOverrideData(
                request.roomTypeId(),
                request.roomId(),
                request.name(),
                request.startDate(),
                request.endDate(),
                request.price(),
                request.weekdays(),
                request.priority()
        );
        RateOverrideTarget target = resolveTarget(request.roomTypeId(), request.roomId());
        return createRateOverrideForTarget(
                target,
                request.roomTypeId(),
                request.roomId(),
                request.name(),
                request.startDate(),
                request.endDate(),
                request.price(),
                request.weekdays(),
                request.priority()
        );
    }

    public RateOverrideResponse createRoomTypeRateOverride(
            String roomTypeCode,
            @Valid RoomTypeRateOverrideCreateRequest request
    ) {
        validateRateOverrideFields(
                request.name(),
                request.startDate(),
                request.endDate(),
                request.price(),
                request.weekdays(),
                request.priority()
        );
        String normalizedCode = normalizeRoomTypeCode(roomTypeCode);
        RoomType roomType = roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", normalizedCode));
        validatePositiveId(roomType.getId(), "Room type id");

        return createRateOverrideForTarget(
                new RateOverrideTarget(roomType, null),
                roomType.getId(),
                null,
                request.name(),
                request.startDate(),
                request.endDate(),
                request.price(),
                request.weekdays(),
                request.priority()
        );
    }

    public RateOverrideResponse updateRateOverride(
            Long id,
            @Valid RateOverrideUpdateRequest request
    ) {
        RateOverride rateOverride = getExistingRateOverride(id);
        validateRateOverrideData(
                request.roomTypeId(),
                request.roomId(),
                request.name(),
                request.startDate(),
                request.endDate(),
                request.price(),
                request.weekdays(),
                request.priority()
        );
        RateOverrideTarget target = resolveTarget(request.roomTypeId(), request.roomId());
        validateNoConflict(
                request.roomTypeId(),
                request.roomId(),
                request.startDate(),
                request.endDate(),
                request.weekdays(),
                request.priority(),
                rateOverride.getId()
        );

        rateOverride.setRoomType(target.roomType());
        rateOverride.setRoom(target.room());
        rateOverride.setName(request.name().strip());
        rateOverride.setStartDate(request.startDate());
        rateOverride.setEndDate(request.endDate());
        rateOverride.setPrice(request.price());
        rateOverride.setWeekdays(weekdayCodec.encodeWeekdays(request.weekdays(), rateOverride.getId()));
        rateOverride.setPriority(request.priority());

        return mapRateOverrideResponse(rateOverrideRepository.save(rateOverride));
    }

    public void deleteRateOverride(Long id) {
        RateOverride rateOverride = getExistingRateOverride(id);
        rateOverride.setIsActive(false);
        rateOverrideRepository.save(rateOverride);
    }

    private RateOverride getExistingRateOverride(Long id) {
        validatePositiveId(id, "Rate override id");
        return rateOverrideRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rate override", id.toString()));
    }

    private RateOverrideTarget resolveTarget(Long roomTypeId, Long roomId) {
        if (roomId != null) {
            Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room", roomId.toString()));
            return new RateOverrideTarget(null, room);
        }

        RoomType roomType = roomTypeRepository.findByIdAndDeletedAtIsNull(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", roomTypeId.toString()));
        return new RateOverrideTarget(roomType, null);
    }

    private RateOverrideResponse createRateOverrideForTarget(
            RateOverrideTarget target,
            Long roomTypeId,
            Long roomId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal price,
            Set<Integer> weekdays,
            Integer priority
    ) {
        validateNoConflict(
                roomTypeId,
                roomId,
                startDate,
                endDate,
                weekdays,
                priority,
                null
        );
        RateOverride rateOverride = RateOverride.builder()
                .roomType(target.roomType())
                .room(target.room())
                .name(name.strip())
                .startDate(startDate)
                .endDate(endDate)
                .price(price)
                .weekdays(weekdayCodec.encodeWeekdays(weekdays, null))
                .priority(priority)
                .isActive(true)
                .build();
        return mapRateOverrideResponse(rateOverrideRepository.save(rateOverride));
    }

    private void validateRateOverrideData(
            Long roomTypeId,
            Long roomId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal price,
            Set<Integer> weekdays,
            Integer priority
    ) {
        if ((roomTypeId == null) == (roomId == null)) {
            throw new BusinessValidationException(
                    "Exactly one of roomTypeId or roomId must be provided"
            );
        }
        if (roomTypeId != null) {
            validatePositiveId(roomTypeId, "Room type id");
        }
        if (roomId != null) {
            validatePositiveId(roomId, "Room id");
        }
        validateRateOverrideFields(name, startDate, endDate, price, weekdays, priority);
    }

    private void validateRateOverrideFields(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal price,
            Set<Integer> weekdays,
            Integer priority
    ) {
        if (name == null || name.isBlank()) {
            throw new BusinessValidationException("Rate override name cannot be blank");
        }
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new BusinessValidationException("End date must be after start date");
        }
        if (price == null || price.signum() < 0) {
            throw new BusinessValidationException("Rate override price must be zero or greater");
        }
        if (priority == null) {
            throw new BusinessValidationException("Rate override priority is required");
        }
        validateWeekdays(weekdays);
    }

    private String normalizeRoomTypeCode(String roomTypeCode) {
        if (roomTypeCode == null || roomTypeCode.isBlank()) {
            throw new BusinessValidationException("Room type code cannot be blank");
        }
        return roomTypeCode.strip().toUpperCase(Locale.ROOT);
    }

    private void validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException(fieldName + " must be a positive number");
        }
    }

    private void validateWeekdays(Set<Integer> weekdays) {
        if (weekdays == null) {
            return;
        }
        if (weekdays.isEmpty()) {
            throw new BusinessValidationException(
                    "Weekdays must be null or contain at least one day"
            );
        }
        for (Integer weekday : weekdays) {
            if (weekday == null || weekday < 1 || weekday > 7) {
                throw new BusinessValidationException("Weekdays must contain only values from 1 to 7");
            }
        }
    }

    private void validateNoConflict(
            Long roomTypeId,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate,
            Set<Integer> weekdays,
            Integer priority,
            Long excludedId
    ) {
        List<RateOverride> candidates = rateOverrideRepository.findActiveConflicts(
                roomId,
                roomTypeId,
                startDate,
                endDate,
                priority,
                excludedId
        );
        for (RateOverride candidate : candidates) {
            Set<Integer> candidateWeekdays = weekdayCodec.decodeWeekdays(
                    candidate.getWeekdays(),
                    candidate.getId()
            );
            if (hasOverlappingApplicableDate(
                    startDate,
                    endDate,
                    weekdays,
                    candidate.getStartDate(),
                    candidate.getEndDate(),
                    candidateWeekdays
            )) {
                throw new RateOverrideConflictException(candidate.getId());
            }
        }
    }

    private boolean hasOverlappingApplicableDate(
            LocalDate startDate,
            LocalDate endDate,
            Set<Integer> weekdays,
            LocalDate candidateStartDate,
            LocalDate candidateEndDate,
            Set<Integer> candidateWeekdays
    ) {
        LocalDate overlapStart = startDate.isAfter(candidateStartDate) ? startDate : candidateStartDate;
        LocalDate overlapEnd = endDate.isBefore(candidateEndDate) ? endDate : candidateEndDate;
        if (!overlapEnd.isAfter(overlapStart)) {
            return false;
        }

        Set<Integer> sharedWeekdays = getSharedWeekdays(weekdays, candidateWeekdays);
        if (sharedWeekdays != null && sharedWeekdays.isEmpty()) {
            return false;
        }
        for (LocalDate date = overlapStart, limit = overlapStart.plusDays(7);
                date.isBefore(overlapEnd) && date.isBefore(limit);
                date = date.plusDays(1)) {
            if (sharedWeekdays == null || sharedWeekdays.contains(date.getDayOfWeek().getValue())) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> getSharedWeekdays(
            Set<Integer> weekdays,
            Set<Integer> candidateWeekdays
    ) {
        if (weekdays == null) {
            return candidateWeekdays;
        }
        if (candidateWeekdays == null) {
            return weekdays;
        }
        Set<Integer> sharedWeekdays = new HashSet<>(weekdays);
        sharedWeekdays.retainAll(candidateWeekdays);
        return sharedWeekdays;
    }

    private RateOverrideResponse mapRateOverrideResponse(RateOverride rateOverride) {
        Set<Integer> decodedWeekdays = weekdayCodec.decodeWeekdays(
                rateOverride.getWeekdays(),
                rateOverride.getId()
        );
        List<Integer> weekdays = decodedWeekdays == null
                ? null
                : decodedWeekdays.stream().sorted().toList();
        return new RateOverrideResponse(
                rateOverride.getId(),
                rateOverride.getRoomType() == null ? null : rateOverride.getRoomType().getCode(),
                rateOverride.getRoomType() == null ? null : rateOverride.getRoomType().getName(),
                rateOverride.getRoom() == null ? null : rateOverride.getRoom().getRoomNumber(),
                rateOverride.getName(),
                rateOverride.getStartDate(),
                rateOverride.getEndDate(),
                rateOverride.getPrice(),
                weekdays,
                rateOverride.getPriority(),
                rateOverride.getIsActive(),
                rateOverride.getCreatedAt(),
                rateOverride.getUpdatedAt()
        );
    }

    private record RateOverrideTarget(RoomType roomType, Room room) {
    }
}
