package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "shift_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"staff_id", "shift_id", "work_date"}),
        indexes = {
                @Index(name = "idx_shift_assign_work_date", columnList = "work_date, shift_id"),
                @Index(name = "idx_shift_assign_staff_date", columnList = "staff_id, work_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAssignment extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private StaffProfile staffProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "shift_start_at", nullable = false)
    private OffsetDateTime shiftStartAt;

    @Column(name = "shift_end_at", nullable = false)
    private OffsetDateTime shiftEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;
}
