package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.HotelProperties;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentCreateRequest;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentResponse;
import com.example.hotelmanagement.dto.shiftassignment.ShiftAssignmentUpdateRequest;
import com.example.hotelmanagement.entity.Shift;
import com.example.hotelmanagement.entity.ShiftAssignment;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.AssignmentStatus;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ShiftOverlapException;
import com.example.hotelmanagement.repositories.ShiftAssignmentRepository;
import com.example.hotelmanagement.repositories.ShiftRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftAssignmentServiceTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 20);
    private static final Long ACTOR_USER_ID = 99L;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private StaffProfileRepository staffProfileRepository;

    @Mock
    private ShiftRepository shiftRepository;

    private ShiftAssignmentService shiftAssignmentService;
    private StaffProfile staffProfile;

    @BeforeEach
    void setUp() {
        shiftAssignmentService = new ShiftAssignmentService(
                shiftAssignmentRepository,
                staffProfileRepository,
                shiftRepository,
                new HotelProperties(ZoneId.of("Asia/Ho_Chi_Minh")),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("UTC"))
        );
        staffProfile = createStaffProfile();
    }

    @Test
    void createShiftAssignmentCalculatesSameDayPeriod() {
        Shift morning = createShift("MORNING", LocalTime.of(6, 0), LocalTime.of(14, 0), false);
        stubAssignmentCreation(morning);

        ShiftAssignmentResponse response = shiftAssignmentService.createShiftAssignment(
                new ShiftAssignmentCreateRequest("nv001", "morning", WORK_DATE, " Front desk "),
                ACTOR_USER_ID
        );

        assertNotNull(response.publicId());
        assertEquals(OffsetDateTime.parse("2026-08-20T06:00:00+07:00"), response.shiftStartAt());
        assertEquals(OffsetDateTime.parse("2026-08-20T14:00:00+07:00"), response.shiftEndAt());
        assertEquals("Front desk", response.note());
        assertEquals(AssignmentStatus.SCHEDULED, response.status());
    }

    @Test
    void createShiftAssignmentMovesMidnightEndToNextDay() {
        Shift night = createShift("NIGHT", LocalTime.of(22, 0), LocalTime.of(6, 0), true);
        stubAssignmentCreation(night);

        ShiftAssignmentResponse response = shiftAssignmentService.createShiftAssignment(
                new ShiftAssignmentCreateRequest("NV001", "NIGHT", WORK_DATE, null),
                ACTOR_USER_ID
        );

        assertEquals(OffsetDateTime.parse("2026-08-20T22:00:00+07:00"), response.shiftStartAt());
        assertEquals(OffsetDateTime.parse("2026-08-21T06:00:00+07:00"), response.shiftEndAt());
    }

    @Test
    void createShiftAssignmentRejectsPastWorkDate() {
        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.createShiftAssignment(
                        new ShiftAssignmentCreateRequest("NV001", "MORNING", WORK_DATE.minusDays(1), null),
                        ACTOR_USER_ID
                )
        );
        verify(staffProfileRepository, never()).findByEmployeeCodeIgnoreCase(any());
        verify(shiftAssignmentRepository, never()).saveAndFlush(any(ShiftAssignment.class));
    }

    @Test
    void updateShiftAssignmentRejectsPastWorkDate() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));

        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.updateShiftAssignment(
                        publicId,
                        new ShiftAssignmentUpdateRequest(
                                "NV001", "MORNING", WORK_DATE.minusDays(1), AssignmentStatus.SCHEDULED, null
                        ),
                        ACTOR_USER_ID
                )
        );
        verify(staffProfileRepository, never()).findByEmployeeCodeIgnoreCase(any());
        verify(shiftAssignmentRepository, never()).saveAndFlush(any(ShiftAssignment.class));
    }

    @Test
    void createShiftAssignmentRejectsOverlap() {
        Shift morning = createShift("MORNING", LocalTime.of(6, 0), LocalTime.of(14, 0), false);
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("NV001"))
                .thenReturn(Optional.of(staffProfile));
        when(shiftRepository.findByCodeIgnoreCase("MORNING")).thenReturn(Optional.of(morning));
        when(shiftAssignmentRepository.existsOverlappingAssignment(
                eq(10L),
                anySet(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(true);

        assertThrows(
                ShiftOverlapException.class,
                () -> shiftAssignmentService.createShiftAssignment(
                        new ShiftAssignmentCreateRequest("NV001", "MORNING", WORK_DATE, null),
                        ACTOR_USER_ID
                )
        );
        verify(shiftAssignmentRepository, never()).saveAndFlush(any(ShiftAssignment.class));
    }

    @Test
    void createShiftAssignmentAllowsAdjacentPeriod() {
        Shift afternoon = createShift("AFTERNOON", LocalTime.of(14, 0), LocalTime.of(22, 0), false);
        stubAssignmentCreation(afternoon);

        ShiftAssignmentResponse response = shiftAssignmentService.createShiftAssignment(
                new ShiftAssignmentCreateRequest("NV001", "AFTERNOON", WORK_DATE, null),
                ACTOR_USER_ID
        );

        assertEquals(OffsetDateTime.parse("2026-08-20T14:00:00+07:00"), response.shiftStartAt());
        verify(shiftAssignmentRepository).existsOverlappingAssignment(
                eq(10L),
                anySet(),
                eq(OffsetDateTime.parse("2026-08-20T14:00:00+07:00")),
                eq(OffsetDateTime.parse("2026-08-20T22:00:00+07:00"))
        );
    }

    @Test
    void updateShiftAssignmentRecalculatesPeriodAndRecordsActor() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        Shift night = createShift("NIGHT", LocalTime.of(22, 0), LocalTime.of(6, 0), true);
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("NV001"))
                .thenReturn(Optional.of(staffProfile));
        when(shiftRepository.findByCodeIgnoreCase("NIGHT")).thenReturn(Optional.of(night));
        when(shiftAssignmentRepository.saveAndFlush(assignment)).thenReturn(assignment);

        ShiftAssignmentResponse response = shiftAssignmentService.updateShiftAssignment(
                publicId,
                new ShiftAssignmentUpdateRequest(
                        "NV001",
                        "NIGHT",
                        WORK_DATE.plusDays(1),
                        AssignmentStatus.SCHEDULED,
                        "Night duty"
                ),
                101L
        );

        assertEquals(OffsetDateTime.parse("2026-08-21T22:00:00+07:00"), response.shiftStartAt());
        assertEquals(OffsetDateTime.parse("2026-08-22T06:00:00+07:00"), response.shiftEndAt());
        assertEquals(101L, assignment.getAssignedBy());
    }

    @Test
    void createShiftAssignmentRejectsInactiveStaff() {
        staffProfile.setEmploymentStatus(EmploymentStatus.ON_LEAVE);
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("NV001"))
                .thenReturn(Optional.of(staffProfile));

        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.createShiftAssignment(
                        new ShiftAssignmentCreateRequest("NV001", "MORNING", WORK_DATE, null),
                        ACTOR_USER_ID
                )
        );
        verify(shiftRepository, never()).findByCodeIgnoreCase(any());
    }

    @Test
    void deleteShiftAssignmentCancelsInsteadOfDeleting() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.saveAndFlush(assignment)).thenReturn(assignment);

        shiftAssignmentService.deleteShiftAssignment(publicId);

        assertEquals(AssignmentStatus.CANCELLED, assignment.getStatus());
        verify(shiftAssignmentRepository).saveAndFlush(assignment);
        verify(shiftAssignmentRepository, never()).delete(any(ShiftAssignment.class));
    }

    @Test
    void getShiftAssignmentsByDateRangeReturnsAssignmentsSortedByRepository() {
        ShiftAssignment assignment = createAssignment(UUID.randomUUID());
        when(shiftAssignmentRepository.findByWorkDateBetweenOrderByWorkDateAscShiftStartAtAsc(
                WORK_DATE, WORK_DATE.plusDays(6)
        )).thenReturn(List.of(assignment));

        List<ShiftAssignmentResponse> responses = shiftAssignmentService.getShiftAssignmentsByDateRange(
                WORK_DATE, WORK_DATE.plusDays(6)
        );

        assertEquals(1, responses.size());
        assertEquals(assignment.getPublicId(), responses.get(0).publicId().toString());
    }

    @Test
    void getShiftAssignmentsByDateRangeRejectsMissingOrInvalidRange() {
        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.getShiftAssignmentsByDateRange(null, WORK_DATE)
        );
        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.getShiftAssignmentsByDateRange(WORK_DATE, WORK_DATE.minusDays(1))
        );
        verify(shiftAssignmentRepository, never())
                .findByWorkDateBetweenOrderByWorkDateAscShiftStartAtAsc(any(), any());
    }

    @Test
    void getShiftAssignmentsByStaffNormalizesEmployeeCodeBeforeQuerying() {
        ShiftAssignment assignment = createAssignment(UUID.randomUUID());
        when(shiftAssignmentRepository.findByStaffProfile_EmployeeCodeIgnoreCaseOrderByWorkDateAscShiftStartAtAsc(
                "NV001"
        )).thenReturn(List.of(assignment));

        List<ShiftAssignmentResponse> responses = shiftAssignmentService.getShiftAssignmentsByStaff(" nv001 ");

        assertEquals(1, responses.size());
        verify(shiftAssignmentRepository)
                .findByStaffProfile_EmployeeCodeIgnoreCaseOrderByWorkDateAscShiftStartAtAsc("NV001");
    }

    @Test
    void getShiftAssignmentsByStaffRejectsBlankEmployeeCode() {
        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.getShiftAssignmentsByStaff("   ")
        );
        verify(shiftAssignmentRepository, never())
                .findByStaffProfile_EmployeeCodeIgnoreCaseOrderByWorkDateAscShiftStartAtAsc(any());
    }

    @Test
    void getOwnShiftAssignmentsQueriesOnlyCurrentUserWithinDateRange() {
        ShiftAssignment assignment = createAssignment(UUID.randomUUID());
        when(shiftAssignmentRepository
                .findByStaffProfile_User_IdAndWorkDateBetweenOrderByWorkDateAscShiftStartAtAsc(
                        99L, WORK_DATE, WORK_DATE.plusDays(13)
                )).thenReturn(List.of(assignment));

        List<ShiftAssignmentResponse> responses = shiftAssignmentService.getOwnShiftAssignments(
                99L, WORK_DATE, WORK_DATE.plusDays(13)
        );

        assertEquals(1, responses.size());
        verify(shiftAssignmentRepository)
                .findByStaffProfile_User_IdAndWorkDateBetweenOrderByWorkDateAscShiftStartAtAsc(
                        99L, WORK_DATE, WORK_DATE.plusDays(13)
                );
    }

    @Test
    void completeOwnShiftAfterEndChangesStatus() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        assignment.setShiftStartAt(OffsetDateTime.parse("2026-08-19T06:00:00+07:00"));
        assignment.setShiftEndAt(OffsetDateTime.parse("2026-08-19T14:00:00+07:00"));
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.saveAndFlush(assignment)).thenReturn(assignment);

        ShiftAssignmentResponse response = shiftAssignmentService.completeOwnShift(publicId, 99L);

        assertEquals(AssignmentStatus.COMPLETED, response.status());
        verify(shiftAssignmentRepository).saveAndFlush(assignment);
    }

    @Test
    void completeOwnShiftBeforeEndIsRejected() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        assignment.setShiftEndAt(OffsetDateTime.parse("2026-08-21T14:00:00+07:00"));
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));

        assertThrows(
                BusinessValidationException.class,
                () -> shiftAssignmentService.completeOwnShift(publicId, 99L)
        );
        verify(shiftAssignmentRepository, never()).saveAndFlush(any(ShiftAssignment.class));
    }

    @Test
    void reportOwnAbsenceTrimsAndStoresNote() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));
        when(shiftAssignmentRepository.saveAndFlush(assignment)).thenReturn(assignment);

        ShiftAssignmentResponse response = shiftAssignmentService.reportOwnAbsence(publicId, 99L, "  Sick leave  ");

        assertEquals(AssignmentStatus.ABSENT, response.status());
        assertEquals("Sick leave", response.note());
    }

    @Test
    void ownShiftCannotBeUpdatedByAnotherUser() {
        UUID publicId = UUID.randomUUID();
        ShiftAssignment assignment = createAssignment(publicId);
        when(shiftAssignmentRepository.findByPublicId(publicId.toString()))
                .thenReturn(Optional.of(assignment));

        assertThrows(
                com.example.hotelmanagement.exceptions.ResourceNotFoundException.class,
                () -> shiftAssignmentService.completeOwnShift(publicId, 100L)
        );
        verify(shiftAssignmentRepository, never()).saveAndFlush(any(ShiftAssignment.class));
    }

    private void stubAssignmentCreation(Shift shift) {
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("NV001"))
                .thenReturn(Optional.of(staffProfile));
        when(shiftRepository.findByCodeIgnoreCase(shift.getCode())).thenReturn(Optional.of(shift));
        when(shiftAssignmentRepository.saveAndFlush(any(ShiftAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StaffProfile createStaffProfile() {
        User user = User.builder()
                .fullName("Nguyen Van A")
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(OffsetDateTime.now())
                .build();
        StaffProfile staff = StaffProfile.builder()
                .user(user)
                .employeeCode("NV001")
                .position("Receptionist")
                .hiredAt(WORK_DATE.minusYears(1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .build();
        staff.setId(10L);
        user.setId(99L);
        return staff;
    }

    private Shift createShift(String code, LocalTime startTime, LocalTime endTime, boolean crossesMidnight) {
        Shift shift = Shift.builder()
                .code(code)
                .name(code)
                .startTime(startTime)
                .endTime(endTime)
                .crossesMidnight(crossesMidnight)
                .isActive(true)
                .build();
        shift.setId((long) code.hashCode() & Integer.MAX_VALUE);
        return shift;
    }

    private ShiftAssignment createAssignment(UUID publicId) {
        Shift morning = createShift("MORNING", LocalTime.of(6, 0), LocalTime.of(14, 0), false);
        ShiftAssignment assignment = ShiftAssignment.builder()
                .publicId(publicId.toString())
                .staffProfile(staffProfile)
                .shift(morning)
                .workDate(WORK_DATE)
                .shiftStartAt(OffsetDateTime.parse("2026-08-20T06:00:00+07:00"))
                .shiftEndAt(OffsetDateTime.parse("2026-08-20T14:00:00+07:00"))
                .status(AssignmentStatus.SCHEDULED)
                .assignedBy(ACTOR_USER_ID)
                .build();
        assignment.setId(50L);
        return assignment;
    }
}
