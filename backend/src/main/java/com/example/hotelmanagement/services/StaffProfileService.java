package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.staffprofile.StaffHireRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffInvitationAcceptRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffManagementListResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffPasswordUpdateRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffListResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileUpdateRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffOwnProfileResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffOwnProfileUpdateRequest;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.UserRole;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RoleRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import com.example.hotelmanagement.repositories.UserRoleRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Validated
@Transactional
public class StaffProfileService {

    private static final String STAFF_ROLE_CODE = "STAFF";
    private static final String EMPLOYEE_CODE_PREFIX = "EMP-";
    private static final String EMPLOYEE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int EMPLOYEE_CODE_SUFFIX_LENGTH = 8;
    private static final int EMPLOYEE_CODE_MAX_ATTEMPTS = 10;

    private final StaffProfileRepository staffProfileRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final AvatarUrlResolver avatarUrlResolver;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public StaffProfileService(
            StaffProfileRepository staffProfileRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            Clock clock,
            AvatarUrlResolver avatarUrlResolver
    ) {
        this.staffProfileRepository = staffProfileRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.avatarUrlResolver = avatarUrlResolver;
    }

    public StaffProfileService(
            StaffProfileRepository staffProfileRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            Clock clock
    ) {
        this(
                staffProfileRepository,
                userRepository,
                userRoleRepository,
                roleRepository,
                passwordEncoder,
                authTokenService,
                emailService,
                refreshTokenService,
                clock,
                null
        );
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse hireStaff(@Valid StaffHireRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }
        String employeeCode = generateEmployeeCode();
        User user = createStaffUser(request);
        replaceUserRole(user, STAFF_ROLE_CODE);

        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode(employeeCode)
                .position(normalizeOptionalText(request.position()))
                .department(normalizeOptionalText(request.department()))
                .hiredAt(request.hiredAt() != null ? request.hiredAt() : LocalDate.now(clock))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .baseSalary(request.baseSalary())
                .build();

        StaffProfileResponse response = mapResponse(staffProfileRepository.saveAndFlush(profile));
        AuthTokenService.IssuedAuthToken token =
                authTokenService.createToken(user, AuthTokenType.STAFF_INVITATION, null);
        emailService.sendStaffInvitationEmail(user.getEmail(), user.getFullName(), token.value());
        return response;
    }

    @Transactional
    public void acceptStaffInvitation(@Valid StaffInvitationAcceptRequest request) {
        AuthToken token = authTokenService.consumeToken(request.token(), AuthTokenType.STAFF_INVITATION);
        User user = token.getUser();
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION || user.getEmailVerifiedAt() != null) {
            throw new BusinessValidationException("Lời mời Staff không còn hiệu lực");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setEmailVerifiedAt(OffsetDateTime.now(clock));
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.saveAndFlush(user);
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public void updateStaffPassword(String employeeCode, @Valid StaffPasswordUpdateRequest request) {
        StaffProfile profile = getExistingProfile(employeeCode);
        User user = profile.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(user);
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public void resendStaffInvitation(String employeeCode) {
        StaffProfile profile = getExistingProfile(employeeCode);
        User user = profile.getUser();
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessValidationException("Chỉ Staff đang chờ xác thực mới nhận được lời mời mới");
        }
        AuthTokenService.IssuedAuthToken token =
                authTokenService.createToken(user, AuthTokenType.STAFF_INVITATION, null);
        emailService.sendStaffInvitationEmail(user.getEmail(), user.getFullName(), token.value());
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse getStaff(String employeeCode) {
        return mapResponse(getExistingProfile(employeeCode));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public List<StaffListResponse> getStaffProfiles(boolean activeOnly) {
        List<StaffProfile> profiles = activeOnly
                ? staffProfileRepository.findByEmploymentStatusAndUser_DeletedAtIsNullOrderByEmployeeCodeAsc(
                        EmploymentStatus.ACTIVE
                )
                : staffProfileRepository.findByUser_DeletedAtIsNullOrderByEmployeeCodeAsc();
        return profiles.stream()
                .filter(profile -> !activeOnly || isAssignableStaff(profile.getUser()))
                .map(this::mapListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public List<StaffManagementListResponse> getStaffManagementProfiles(boolean activeOnly) {
        List<StaffProfile> profiles = activeOnly
                ? staffProfileRepository.findByEmploymentStatusAndUser_DeletedAtIsNullOrderByEmployeeCodeAsc(
                        EmploymentStatus.ACTIVE
                )
                : staffProfileRepository.findByUser_DeletedAtIsNullOrderByEmployeeCodeAsc();
        return profiles.stream().map(this::mapManagementListResponse).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STAFF')")
    public StaffOwnProfileResponse getOwnProfile(Long userId) {
        return mapOwnProfileResponse(getExistingProfileByUserId(userId));
    }

    @PreAuthorize("hasRole('STAFF')")
    public StaffOwnProfileResponse updateOwnProfile(Long userId, @Valid StaffOwnProfileUpdateRequest request) {
        StaffProfile profile = getExistingProfileByUserId(userId);
        User user = profile.getUser();
        String normalizedPhone = normalizeOptionalText(request.phone());
        ensurePhoneIsAvailable(normalizedPhone, user);
        user.setPhone(normalizedPhone);
        userRepository.save(user);
        return mapOwnProfileResponse(profile);
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse editStaff(String employeeCode, @Valid StaffProfileUpdateRequest request) {
        StaffProfile profile = getExistingProfile(employeeCode);

        if (request.position() != null) {
            profile.setPosition(normalizeOptionalText(request.position()));
        }
        if (request.department() != null) {
            profile.setDepartment(normalizeOptionalText(request.department()));
        }
        if (request.baseSalary() != null) {
            profile.setBaseSalary(request.baseSalary());
        }

        return mapResponse(staffProfileRepository.saveAndFlush(profile));
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public void deactivateStaff(String employeeCode) {
        updateEmploymentStatus(employeeCode, EmploymentStatus.TERMINATED);
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse updateEmploymentStatus(
            String employeeCode,
            EmploymentStatus requestedStatus
    ) {
        if (requestedStatus == null) {
            throw new BusinessValidationException("Employment status không được để trống");
        }
        StaffProfile profile = getExistingProfile(employeeCode);
        if (profile.getEmploymentStatus() == EmploymentStatus.TERMINATED
                && requestedStatus != EmploymentStatus.TERMINATED) {
            throw new BusinessValidationException("TERMINATED Staff không thể được khôi phục");
        }
        if (requestedStatus == EmploymentStatus.TERMINATED) {
            terminateStaff(profile);
        } else {
            profile.setEmploymentStatus(requestedStatus);
            profile.setTerminatedAt(null);
        }
        return mapResponse(staffProfileRepository.saveAndFlush(profile));
    }

    private User createStaffUser(StaffHireRequest request) {
        User user = User.builder()
                .publicId(java.util.UUID.randomUUID().toString())
                .email(normalizeEmail(request.email()))
                .passwordHash(passwordEncoder.encode(request.temporaryPassword()))
                .phone(normalizeOptionalText(request.phone()))
                .fullName(request.fullName().strip())
                .status(UserStatus.PENDING_VERIFICATION)
                .failedLoginCount(0)
                .build();
        return userRepository.saveAndFlush(user);
    }

    private String generateEmployeeCode() {
        for (int attempt = 0; attempt < EMPLOYEE_CODE_MAX_ATTEMPTS; attempt++) {
            StringBuilder suffix = new StringBuilder(EMPLOYEE_CODE_SUFFIX_LENGTH);
            for (int index = 0; index < EMPLOYEE_CODE_SUFFIX_LENGTH; index++) {
                int characterIndex = secureRandom.nextInt(EMPLOYEE_CODE_ALPHABET.length());
                suffix.append(EMPLOYEE_CODE_ALPHABET.charAt(characterIndex));
            }
            String candidate = EMPLOYEE_CODE_PREFIX + suffix;
            if (staffProfileRepository.findByEmployeeCodeIgnoreCase(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo mã nhân viên duy nhất");
    }

    private void replaceUserRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Role " + roleCode + " chưa được seed"));
        userRoleRepository.deleteByUser_Id(user.getId());
        userRoleRepository.flush();
        user.getUserRoles().clear();
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .assignedAt(OffsetDateTime.now(clock))
                .build();
        user.getUserRoles().add(userRole);
        userRoleRepository.saveAndFlush(userRole);
    }

    private void terminateStaff(StaffProfile profile) {
        User user = profile.getUser();
        if (profile.getEmailAtTermination() == null) {
            profile.setEmailAtTermination(user.getEmail());
        }
        profile.setEmploymentStatus(EmploymentStatus.TERMINATED);
        profile.setTerminatedAt(LocalDate.now(clock));
        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);
    }

    private StaffProfile getExistingProfile(String employeeCode) {
        String normalizedEmployeeCode = normalizeUpper(employeeCode, "Employee code");
        return staffProfileRepository.findByEmployeeCodeIgnoreCase(normalizedEmployeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", normalizedEmployeeCode));
    }

    private StaffProfile getExistingProfileByUserId(Long userId) {
        return staffProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", userId.toString()));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private boolean isAssignableStaff(User user) {
        return user.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() != null;
    }

    private void ensurePhoneIsAvailable(String phone, User user) {
        if (phone == null || phone.equals(user.getPhone())) {
            return;
        }
        userRepository.findByPhoneAndDeletedAtIsNull(phone)
                .filter(existingUser -> !existingUser.getId().equals(user.getId()))
                .ifPresent(existingUser -> {
                    throw new DuplicateResourceException("User", "phone", phone);
                });
    }

    private String normalizeUpper(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, fieldName + " cannot be blank");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.strip();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }

    private StaffProfileResponse mapResponse(StaffProfile profile) {
        User user = profile.getUser();
        return new StaffProfileResponse(
                profile.getEmployeeCode(),
                user.getPublicId(),
                user.getFullName(),
                user.getEmail(),
                profile.getPosition(),
                profile.getDepartment(),
                profile.getHiredAt(),
                profile.getTerminatedAt(),
                profile.getEmploymentStatus(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                profile.getBaseSalary(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private StaffListResponse mapListResponse(StaffProfile profile) {
        User user = profile.getUser();
        return new StaffListResponse(
                profile.getEmployeeCode(),
                user.getFullName(),
                profile.getPosition(),
                profile.getDepartment(),
                profile.getEmploymentStatus()
        );
    }

    private StaffManagementListResponse mapManagementListResponse(StaffProfile profile) {
        User user = profile.getUser();
        return new StaffManagementListResponse(
                profile.getEmployeeCode(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                profile.getPosition(),
                profile.getDepartment(),
                profile.getEmploymentStatus(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                profile.getHiredAt(),
                profile.getTerminatedAt(),
                profile.getBaseSalary()
        );
    }

    private StaffOwnProfileResponse mapOwnProfileResponse(StaffProfile profile) {
        User user = profile.getUser();
        return new StaffOwnProfileResponse(
                profile.getEmployeeCode(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                avatarUrlResolver == null ? user.getAvatarUrl() : avatarUrlResolver.resolve(user),
                profile.getPosition(),
                profile.getDepartment(),
                profile.getHiredAt(),
                profile.getEmploymentStatus()
        );
    }
}
