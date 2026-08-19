package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.shift.ShiftCreateRequest;
import com.example.hotelmanagement.dto.shift.ShiftResponse;
import com.example.hotelmanagement.dto.shift.ShiftUpdateRequest;
import com.example.hotelmanagement.entity.Shift;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.ShiftRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

@Service
@Validated
@Transactional
@PreAuthorize(PermissionExpressions.SHIFT_MANAGE)
public class ShiftService {

    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> getShifts() {
        return shiftRepository.findAllByOrderByCodeAsc()
                .stream()
                .map(this::mapShiftResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse getShift(String code) {
        return mapShiftResponse(getExistingShift(code));
    }

    public ShiftResponse createShift(@Valid ShiftCreateRequest request) {
        String normalizedCode = normalizeCode(request.code());
        if (shiftRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateResourceException("Shift", "code", normalizedCode);
        }

        boolean crossesMidnight = getValueOrDefault(request.crossesMidnight(), false);
        validateShiftTimes(request.startTime(), request.endTime(), crossesMidnight);

        Shift shift = Shift.builder()
                .code(normalizedCode)
                .name(request.name().strip())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .crossesMidnight(crossesMidnight)
                .isActive(getValueOrDefault(request.isActive(), true))
                .build();

        return mapShiftResponse(shiftRepository.save(shift));
    }

    public ShiftResponse updateShift(String code, @Valid ShiftUpdateRequest request) {
        Shift shift = getExistingShift(code);
        validateShiftTimes(request.startTime(), request.endTime(), request.crossesMidnight());

        shift.setName(request.name().strip());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setCrossesMidnight(request.crossesMidnight());
        if (request.isActive() != null) {
            shift.setIsActive(request.isActive());
        }

        return mapShiftResponse(shiftRepository.save(shift));
    }

    public void deleteShift(String code) {
        Shift shift = getExistingShift(code);
        shift.setIsActive(false);
        shiftRepository.save(shift);
    }

    private Shift getExistingShift(String code) {
        String normalizedCode = normalizeCode(code);
        return shiftRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", normalizedCode));
    }

    private void validateShiftTimes(LocalTime startTime, LocalTime endTime, boolean crossesMidnight) {
        if (startTime.equals(endTime)) {
            throw new BusinessValidationException("Shift start time and end time must be different");
        }
        if (crossesMidnight && endTime.isAfter(startTime)) {
            throw new BusinessValidationException(
                    "A shift crossing midnight must end at or before its start time"
            );
        }
        if (!crossesMidnight && !endTime.isAfter(startTime)) {
            throw new BusinessValidationException(
                    "A shift not crossing midnight must end after its start time"
            );
        }
    }

    private ShiftResponse mapShiftResponse(Shift shift) {
        return new ShiftResponse(
                shift.getCode(),
                shift.getName(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getCrossesMidnight(),
                shift.getIsActive(),
                shift.getCreatedAt(),
                shift.getUpdatedAt()
        );
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Shift code cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private boolean getValueOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }
}
