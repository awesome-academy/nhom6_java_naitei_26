package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.HotelProperties;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentCreateRequest;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentResponse;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentUpdateRequest;
import com.example.hotelmanagement.entity.Shift;
import com.example.hotelmanagement.entity.ShiftAssignment;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.AssignmentStatus;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.ShiftOverlapException;
import com.example.hotelmanagement.repositories.ShiftAssignmentRepository;
import com.example.hotelmanagement.repositories.ShiftRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Validated
@Transactional
@PreAuthorize(PermissionExpressions.SHIFT_MANAGE)
public class ShiftAssignmentService {

    private static final Set<AssignmentStatus> EFFECTIVE_STATUSES = Set.of(
            AssignmentStatus.SCHEDULED,
            AssignmentStatus.COMPLETED
    );

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final ShiftRepository shiftRepository;
    private final HotelProperties hotelProperties;

    public ShiftAssignmentService(
            ShiftAssignmentRepository shiftAssignmentRepository,
            StaffProfileRepository staffProfileRepository,
            ShiftRepository shiftRepository,
            HotelProperties hotelProperties
    ) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.shiftRepository = shiftRepository;
        this.hotelProperties = hotelProperties;
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> getShiftAssignments() {
        return shiftAssignmentRepository.findAllByOrderByWorkDateAscShiftStartAtAsc()
                .stream()
                .map(this::mapShiftAssignmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftAssignmentResponse getShiftAssignment(UUID publicId) {
        return mapShiftAssignmentResponse(getExistingAssignment(publicId));
    }

    /** BE-8.1: lịch ca theo ngày/tuần — cả hai đầu khoảng đều bao gồm (inclusive). */
    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> getShiftAssignmentsByDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessValidationException("Both 'from' and 'to' dates are required");
        }
        if (to.isBefore(from)) {
            throw new BusinessValidationException("'to' date cannot be before 'from' date");
        }
        return shiftAssignmentRepository.findByWorkDateBetweenOrderByWorkDateAscShiftStartAtAsc(from, to)
                .stream()
                .map(this::mapShiftAssignmentResponse)
                .toList();
    }

    /** BE-8.1: lịch ca theo staff — xem một nhân viên trực ngày nào. */
    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> getShiftAssignmentsByStaff(String employeeCode) {
        String normalizedEmployeeCode = normalizeCode(employeeCode, "Employee code");
        return shiftAssignmentRepository
                .findByStaffProfile_EmployeeCodeIgnoreCaseOrderByWorkDateAscShiftStartAtAsc(normalizedEmployeeCode)
                .stream()
                .map(this::mapShiftAssignmentResponse)
                .toList();
    }

    public ShiftAssignmentResponse createShiftAssignment(
            @Valid ShiftAssignmentCreateRequest request,
            Long assignedBy
    ) {
        StaffProfile staffProfile = getActiveStaffProfile(request.employeeCode());
        Shift shift = getActiveShift(request.shiftCode());
        ShiftPeriod period = calculateShiftPeriod(request.workDate(), shift);

        validateDuplicateAssignment(staffProfile, shift, request.workDate(), null);
        validateOverlap(staffProfile, period, null);

        ShiftAssignment assignment = ShiftAssignment.builder()
                .publicId(UUID.randomUUID().toString())
                .staffProfile(staffProfile)
                .shift(shift)
                .workDate(request.workDate())
                .shiftStartAt(period.startAt())
                .shiftEndAt(period.endAt())
                .status(AssignmentStatus.SCHEDULED)
                .note(normalizeOptionalText(request.note()))
                .assignedBy(assignedBy)
                .build();

        return mapShiftAssignmentResponse(shiftAssignmentRepository.saveAndFlush(assignment));
    }

    public ShiftAssignmentResponse updateShiftAssignment(
            UUID publicId,
            @Valid ShiftAssignmentUpdateRequest request,
            Long assignedBy
    ) {
        ShiftAssignment assignment = getExistingAssignment(publicId);
        StaffProfile staffProfile = getActiveStaffProfile(request.employeeCode());
        Shift shift = getActiveShift(request.shiftCode());
        ShiftPeriod period = calculateShiftPeriod(request.workDate(), shift);

        validateDuplicateAssignment(staffProfile, shift, request.workDate(), assignment.getId());
        if (EFFECTIVE_STATUSES.contains(request.status())) {
            validateOverlap(staffProfile, period, assignment.getId());
        }

        assignment.setStaffProfile(staffProfile);
        assignment.setShift(shift);
        assignment.setWorkDate(request.workDate());
        assignment.setShiftStartAt(period.startAt());
        assignment.setShiftEndAt(period.endAt());
        assignment.setStatus(request.status());
        assignment.setNote(normalizeOptionalText(request.note()));
        assignment.setAssignedBy(assignedBy);

        return mapShiftAssignmentResponse(shiftAssignmentRepository.saveAndFlush(assignment));
    }

    public void deleteShiftAssignment(UUID publicId) {
        ShiftAssignment assignment = getExistingAssignment(publicId);
        assignment.setStatus(AssignmentStatus.CANCELLED);
        shiftAssignmentRepository.saveAndFlush(assignment);
    }

    private ShiftAssignment getExistingAssignment(UUID publicId) {
        return shiftAssignmentRepository.findByPublicId(publicId.toString())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift assignment",
                        publicId.toString()
                ));
    }

    private StaffProfile getActiveStaffProfile(String employeeCode) {
        String normalizedEmployeeCode = normalizeCode(employeeCode, "Employee code");
        StaffProfile staffProfile = staffProfileRepository
                .findByEmployeeCodeIgnoreCase(normalizedEmployeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", normalizedEmployeeCode));
        if (staffProfile.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BusinessValidationException("Only active Staff can be assigned to a shift");
        }
        return staffProfile;
    }

    private Shift getActiveShift(String shiftCode) {
        String normalizedShiftCode = normalizeCode(shiftCode, "Shift code");
        Shift shift = shiftRepository.findByCodeIgnoreCase(normalizedShiftCode)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", normalizedShiftCode));
        if (!Boolean.TRUE.equals(shift.getIsActive())) {
            throw new BusinessValidationException("Only active shifts can be assigned");
        }
        return shift;
    }

    private ShiftPeriod calculateShiftPeriod(LocalDate workDate, Shift shift) {
        OffsetDateTime startAt = workDate
                .atTime(shift.getStartTime())
                .atZone(hotelProperties.timeZone())
                .toOffsetDateTime();

        LocalDate endDate = workDate;
        if (Boolean.TRUE.equals(shift.getCrossesMidnight())
                && !shift.getEndTime().isAfter(shift.getStartTime())) {
            endDate = endDate.plusDays(1);
        }

        OffsetDateTime endAt = endDate
                .atTime(shift.getEndTime())
                .atZone(hotelProperties.timeZone())
                .toOffsetDateTime();

        if (!endAt.isAfter(startAt)) {
            throw new BusinessValidationException("Calculated shift end must be after shift start");
        }
        return new ShiftPeriod(startAt, endAt);
    }

    private void validateDuplicateAssignment(
            StaffProfile staffProfile,
            Shift shift,
            LocalDate workDate,
            Long excludedAssignmentId
    ) {
        boolean duplicate = excludedAssignmentId == null
                ? shiftAssignmentRepository.existsByStaffProfileIdAndShiftIdAndWorkDate(
                        staffProfile.getId(),
                        shift.getId(),
                        workDate
                )
                : shiftAssignmentRepository.existsByStaffProfileIdAndShiftIdAndWorkDateAndIdNot(
                        staffProfile.getId(),
                        shift.getId(),
                        workDate,
                        excludedAssignmentId
                );
        if (duplicate) {
            throw new DuplicateResourceException(
                    "Shift assignment",
                    "staff/shift/workDate",
                    staffProfile.getEmployeeCode() + "/" + shift.getCode() + "/" + workDate
            );
        }
    }

    private void validateOverlap(
            StaffProfile staffProfile,
            ShiftPeriod period,
            Long excludedAssignmentId
    ) {
        boolean overlaps = excludedAssignmentId == null
                ? shiftAssignmentRepository.existsOverlappingAssignment(
                        staffProfile.getId(),
                        EFFECTIVE_STATUSES,
                        period.startAt(),
                        period.endAt()
                )
                : shiftAssignmentRepository.existsOverlappingAssignmentExcludingId(
                        staffProfile.getId(),
                        excludedAssignmentId,
                        EFFECTIVE_STATUSES,
                        period.startAt(),
                        period.endAt()
                );
        if (overlaps) {
            throw new ShiftOverlapException(
                    staffProfile.getEmployeeCode(),
                    period.startAt(),
                    period.endAt()
            );
        }
    }

    private ShiftAssignmentResponse mapShiftAssignmentResponse(ShiftAssignment assignment) {
        return new ShiftAssignmentResponse(
                assignment.getPublicId(),
                assignment.getStaffProfile().getEmployeeCode(),
                assignment.getStaffProfile().getUser().getFullName(),
                assignment.getShift().getCode(),
                assignment.getShift().getName(),
                assignment.getWorkDate(),
                assignment.getShiftStartAt(),
                assignment.getShiftEndAt(),
                assignment.getStatus(),
                assignment.getNote(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }

    private String normalizeCode(String code, String fieldName) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ShiftPeriod(OffsetDateTime startAt, OffsetDateTime endAt) {
    }
}
