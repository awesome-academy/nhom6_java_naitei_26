package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.staffprofile.StaffHireRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileUpdateRequest;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RoleRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffProfileServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private EmailService emailService;

    private StaffProfileService staffProfileService;

    @BeforeEach
    void setUp() {
        staffProfileService = new StaffProfileService(
                staffProfileRepository,
                userRepository,
                roleRepository,
                passwordEncoder,
                authTokenService,
                emailService,
                FIXED_CLOCK
        );
    }

    @Test
    void hireStaffCreatesNewUserWhenEmailNotFound() {
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0001")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("newstaff@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(staffProfileRepository.existsByUser_Id(10L)).thenReturn(false);
        Role staffRole = Role.builder().code("STAFF").build();
        when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(staffRole));
        when(authTokenService.createToken(any(User.class), any(), any()))
                .thenReturn(new AuthTokenService.IssuedAuthToken("raw-token", null));
        when(staffProfileRepository.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StaffHireRequest request = new StaffHireRequest(
                "NewStaff@Example.com", "New Staff", null, "emp-0001", "Receptionist",
                null, null, new BigDecimal("500.00")
        );

        StaffProfileResponse response = staffProfileService.hireStaff(request);

        assertThat(response.employeeCode()).isEqualTo("EMP-0001");
        assertThat(response.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(response.hiredAt()).isEqualTo(LocalDate.now(FIXED_CLOCK));
        verify(emailService).sendPasswordResetEmail("newstaff@example.com", "raw-token");
    }

    @Test
    void hireStaffRejectsDuplicateEmployeeCode() {
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0001"))
                .thenReturn(Optional.of(new StaffProfile()));

        StaffHireRequest request = new StaffHireRequest(
                "staff@example.com", "Staff", null, "emp-0001", "Receptionist", null, null, null
        );

        assertThatThrownBy(() -> staffProfileService.hireStaff(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).findByEmailIgnoreCaseAndDeletedAtIsNull(any());
    }

    @Test
    void hireStaffRejectsWhenUserAlreadyHasProfile() {
        User existingUser = createUser(5L, "staff@example.com");
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0002")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("staff@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(staffProfileRepository.existsByUser_Id(5L)).thenReturn(true);

        StaffHireRequest request = new StaffHireRequest(
                "staff@example.com", "Staff", null, "emp-0002", "Receptionist", null, null, null
        );

        assertThatThrownBy(() -> staffProfileService.hireStaff(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(staffProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void editStaffUpdatesOnlyProvidedFields() {
        User user = createUser(5L, "staff@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .position("Receptionist")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .build();
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0001")).thenReturn(Optional.of(profile));
        when(staffProfileRepository.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StaffProfileResponse response = staffProfileService.editStaff(
                "EMP-0001",
                new StaffProfileUpdateRequest(null, "Front Office", new BigDecimal("800.00"))
        );

        assertThat(response.position()).isEqualTo("Receptionist");
        assertThat(response.department()).isEqualTo("Front Office");
        assertThat(response.baseSalary()).isEqualByComparingTo("800.00");
    }

    @Test
    void deactivateStaffSetsTerminatedStatusAndDate() {
        User user = createUser(5L, "staff@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .position("Receptionist")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .build();
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0001")).thenReturn(Optional.of(profile));
        when(staffProfileRepository.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        staffProfileService.deactivateStaff("EMP-0001");

        assertThat(profile.getEmploymentStatus()).isEqualTo(EmploymentStatus.TERMINATED);
        assertThat(profile.getTerminatedAt()).isEqualTo(LocalDate.now(FIXED_CLOCK));
    }

    @Test
    void getStaffThrowsWhenNotFound() {
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffProfileService.getStaff("EMP-9999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User createUser(Long id, String email) {
        User user = User.builder()
                .publicId("public-" + id)
                .email(email)
                .fullName("Staff")
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        user.setId(id);
        return user;
    }
}
