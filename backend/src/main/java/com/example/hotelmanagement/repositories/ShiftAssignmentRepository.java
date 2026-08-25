package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.ShiftAssignment;
import com.example.hotelmanagement.entity.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @EntityGraph(attributePaths = {"staffProfile.user", "shift"})
    List<ShiftAssignment> findAllByOrderByWorkDateAscShiftStartAtAsc();

    @EntityGraph(attributePaths = {"staffProfile.user", "shift"})
    Optional<ShiftAssignment> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"staffProfile.user", "shift"})
    List<ShiftAssignment> findByWorkDateBetweenOrderByWorkDateAscShiftStartAtAsc(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"staffProfile.user", "shift"})
    List<ShiftAssignment> findByStaffProfile_EmployeeCodeIgnoreCaseOrderByWorkDateAscShiftStartAtAsc(
            String employeeCode
    );

    @Query("""
            SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
            FROM ShiftAssignment assignment
            WHERE assignment.staffProfile.id = :staffId
              AND assignment.shift.id = :shiftId
              AND assignment.workDate = :workDate
              AND assignment.status IN :statuses
            """)
    boolean existsByStaffProfileIdAndShiftIdAndWorkDateAndStatusIn(
            Long staffId,
            Long shiftId,
            LocalDate workDate,
            @Param("statuses") Set<AssignmentStatus> statuses
    );

    @Query("""
            SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
            FROM ShiftAssignment assignment
            WHERE assignment.staffProfile.id = :staffId
              AND assignment.shift.id = :shiftId
              AND assignment.workDate = :workDate
              AND assignment.id <> :assignmentId
              AND assignment.status IN :statuses
            """)
    boolean existsByStaffProfileIdAndShiftIdAndWorkDateAndIdNotAndStatusIn(
            Long staffId,
            Long shiftId,
            LocalDate workDate,
            Long assignmentId,
            @Param("statuses") Set<AssignmentStatus> statuses
    );

    @Query("""
            SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
            FROM ShiftAssignment assignment
            WHERE assignment.staffProfile.id = :staffId
              AND assignment.status IN :statuses
              AND assignment.shiftStartAt < :endAt
              AND assignment.shiftEndAt > :startAt
            """)
    boolean existsOverlappingAssignment(
            @Param("staffId") Long staffId,
            @Param("statuses") Set<AssignmentStatus> statuses,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    @Query("""
            SELECT CASE WHEN COUNT(assignment) > 0 THEN true ELSE false END
            FROM ShiftAssignment assignment
            WHERE assignment.staffProfile.id = :staffId
              AND assignment.id <> :assignmentId
              AND assignment.status IN :statuses
              AND assignment.shiftStartAt < :endAt
              AND assignment.shiftEndAt > :startAt
            """)
    boolean existsOverlappingAssignmentExcludingId(
            @Param("staffId") Long staffId,
            @Param("assignmentId") Long assignmentId,
            @Param("statuses") Set<AssignmentStatus> statuses,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );
}
