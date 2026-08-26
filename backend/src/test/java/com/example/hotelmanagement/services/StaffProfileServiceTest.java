package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.staffprofile.StaffHireRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffInvitationAcceptRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffInvitationResendRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffManagementListResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffPasswordUpdateRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffListResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileUpdateRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffOwnProfileUpdateRequest;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock
    private RefreshTokenService refreshTokenService;

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
                refreshTokenService,
                FIXED_CLOCK
        );
    }

    @Test
    void hireStaffCreatesNewUserWhenEmailNotFound() {
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("newstaff@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        Role staffRole = Role.builder().code("STAFF").build();
        when(roleRepository.findByCode("STAFF")).thenReturn(Optional.of(staffRole));
        when(authTokenService.createToken(any(User.class), any(), any()))
                .thenReturn(new AuthTokenService.IssuedAuthToken("raw-token", null));
        when(staffProfileRepository.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StaffHireRequest request = new StaffHireRequest(
                "NewStaff@Example.com", "New Staff", "temporary-password-123", null, "Receptionist",
                null, null, new BigDecimal("500.00")
        );

        StaffProfileResponse response = staffProfileService.hireStaff(request);

        assertThat(response.employeeCode()).startsWith("EMP-").hasSize(12);
        assertThat(response.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(response.hiredAt()).isEqualTo(LocalDate.now(FIXED_CLOCK));
        ArgumentCaptor<StaffProfile> profileCaptor = ArgumentCaptor.forClass(StaffProfile.class);
        verify(staffProfileRepository).saveAndFlush(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUser().getId()).isEqualTo(10L);
        assertThat(profileCaptor.getValue().getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        verify(emailService).sendStaffInvitationEmail("newstaff@example.com", "New Staff", "raw-token", "temporary-password-123");
    }

    @Test
    void hireStaffRejectsWhenUserAlreadyHasProfile() {
        when(userRepository.existsByEmailIgnoreCase("staff@example.com"))
                .thenReturn(true);

        StaffHireRequest request = new StaffHireRequest(
                "staff@example.com", "Staff", "temporary-password-123", null, "Receptionist", null, null, null
        );

        assertThatThrownBy(() -> staffProfileService.hireStaff(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(staffProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptStaffInvitationActivatesAccountWithoutReplacingTemporaryPassword() {
        User user = createUser(10L, "invited@example.com");
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setEmailVerifiedAt(null);
        AuthToken token = AuthToken.builder().user(user).tokenType(AuthTokenType.STAFF_INVITATION).build();
        when(authTokenService.consumeToken("invitation-token", AuthTokenType.STAFF_INVITATION)).thenReturn(token);
        user.setPasswordHash("temporary-hash");

        staffProfileService.acceptStaffInvitation(new StaffInvitationAcceptRequest("invitation-token"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(java.time.OffsetDateTime.now(FIXED_CLOCK));
        assertThat(user.getPasswordHash()).isEqualTo("temporary-hash");
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void adminCanResetStaffPasswordAndRevokesSessions() {
        User user = createUser(11L, "reset@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user).employeeCode("EMP-0002").hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE).build();
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0002")).thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        staffProfileService.updateStaffPassword("EMP-0002", new StaffPasswordUpdateRequest("new-password-123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void resendStaffInvitationReplacesTemporaryPasswordAndSendsIt() {
        User user = createUser(11L, "pending@example.com");
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setEmailVerifiedAt(null);
        StaffProfile profile = StaffProfile.builder()
                .user(user).employeeCode("EMP-0002").hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE).build();
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0002")).thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("new-temporary-password")).thenReturn("new-hash");
        when(authTokenService.createToken(user, AuthTokenType.STAFF_INVITATION, null))
                .thenReturn(new AuthTokenService.IssuedAuthToken("new-token", null));

        staffProfileService.resendStaffInvitation(
                "EMP-0002", new StaffInvitationResendRequest("new-temporary-password")
        );

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(emailService).sendStaffInvitationEmail(
                "pending@example.com", "Staff", "new-token", "new-temporary-password"
        );
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
        assertThat(profile.getEmailAtTermination()).isEqualTo("staff@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void updateEmploymentStatusAllowsOnLeaveWithoutDeactivatingUser() {
        User user = createUser(5L, "staff@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .build();
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0001")).thenReturn(Optional.of(profile));
        when(staffProfileRepository.saveAndFlush(any(StaffProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StaffProfileResponse response = staffProfileService.updateEmploymentStatus(
                "EMP-0001", EmploymentStatus.ON_LEAVE
        );

        assertThat(response.employmentStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void updateEmploymentStatusRejectsRecoveryFromTerminated() {
        User user = createUser(5L, "staff@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.TERMINATED)
                .terminatedAt(LocalDate.of(2026, 2, 1))
                .build();
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-0001")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> staffProfileService.updateEmploymentStatus("EMP-0001", EmploymentStatus.ACTIVE))
                .isInstanceOf(com.example.hotelmanagement.exceptions.BusinessValidationException.class);
    }

    @Test
    void getStaffThrowsWhenNotFound() {
        when(staffProfileRepository.findByEmployeeCodeIgnoreCase("EMP-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffProfileService.getStaff("EMP-9999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStaffProfilesReturnsOnlySchedulingFieldsForActiveStaff() {
        User user = createUser(5L, "staff@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .position("Receptionist")
                .department("Front Office")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .baseSalary(new BigDecimal("800.00"))
                .build();
        when(staffProfileRepository
                .findByEmploymentStatusAndUser_DeletedAtIsNullOrderByEmployeeCodeAsc(EmploymentStatus.ACTIVE))
                .thenReturn(List.of(profile));

        List<StaffListResponse> response = staffProfileService.getStaffProfiles(true);

        assertThat(response).singleElement().satisfies(staff -> {
            assertThat(staff.employeeCode()).isEqualTo("EMP-0001");
            assertThat(staff.fullName()).isEqualTo("Staff");
            assertThat(staff.position()).isEqualTo("Receptionist");
            assertThat(staff.department()).isEqualTo("Front Office");
            assertThat(staff.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        });
        verify(staffProfileRepository)
                .findByEmploymentStatusAndUser_DeletedAtIsNullOrderByEmployeeCodeAsc(EmploymentStatus.ACTIVE);
    }

    @Test
    void getStaffManagementProfilesIncludesPhoneAndSalary() {
        User user = createUser(5L, "staff@example.com");
        user.setPhone("0901234567");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .position("Receptionist")
                .department("Front Office")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .baseSalary(new BigDecimal("8000000.00"))
                .build();
        when(staffProfileRepository.findByUser_DeletedAtIsNullOrderByEmployeeCodeAsc())
                .thenReturn(List.of(profile));

        List<StaffManagementListResponse> response = staffProfileService.getStaffManagementProfiles(false);

        assertThat(response).singleElement().satisfies(staff -> {
            assertThat(staff.phone()).isEqualTo("0901234567");
            assertThat(staff.baseSalary()).isEqualByComparingTo("8000000.00");
        });
    }

    @Test
    void updateOwnProfileOnlyUpdatesPhoneForAuthenticatedStaff() {
        User user = createUser(5L, "staff@example.com");
        user.setPhone("0901000000");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .position("Receptionist")
                .department("Front Office")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .baseSalary(new BigDecimal("800.00"))
                .build();
        when(staffProfileRepository.findByUser_Id(5L)).thenReturn(Optional.of(profile));

        var response = staffProfileService.updateOwnProfile(5L, new StaffOwnProfileUpdateRequest(" 0902000000 "));

        assertThat(user.getPhone()).isEqualTo("0902000000");
        assertThat(response.phone()).isEqualTo("0902000000");
        assertThat(response.employeeCode()).isEqualTo("EMP-0001");
        verify(userRepository).save(user);
    }

    @Test
    void updateOwnProfileRejectsPhoneAlreadyAssignedToAnotherUser() {
        User user = createUser(5L, "staff@example.com");
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode("EMP-0001")
                .hiredAt(LocalDate.of(2026, 1, 1))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .build();
        User existingUser = createUser(6L, "other@example.com");
        existingUser.setPhone("0902000000");
        when(staffProfileRepository.findByUser_Id(5L)).thenReturn(Optional.of(profile));
        when(userRepository.findByPhoneAndDeletedAtIsNull("0902000000")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> staffProfileService.updateOwnProfile(
                5L,
                new StaffOwnProfileUpdateRequest("0902000000")
        )).isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(user);
    }

    private User createUser(Long id, String email) {
        User user = User.builder()
                .publicId("public-" + id)
                .email(email)
                .fullName("Staff")
                .emailVerifiedAt(java.time.OffsetDateTime.now(FIXED_CLOCK))
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        user.setId(id);
        return user;
    }
}
