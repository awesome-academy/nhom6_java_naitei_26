package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "staff_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(length = 80)
    private String position;

    @Column(length = 80)
    private String department;

    @Column(name = "hired_at", nullable = false)
    private LocalDate hiredAt;

    @Column(name = "terminated_at")
    private LocalDate terminatedAt;

    @Column(name = "email_at_termination", length = 255)
    private String emailAtTermination;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false)
    @Builder.Default
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Column(name = "base_salary", precision = 14, scale = 2)
    private BigDecimal baseSalary;

    @OneToMany(mappedBy = "staffProfile", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<ShiftAssignment> shiftAssignments = new HashSet<>();
}
