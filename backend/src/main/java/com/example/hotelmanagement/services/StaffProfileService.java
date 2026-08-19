package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.staffprofile.StaffHireRequest;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileResponse;
import com.example.hotelmanagement.dto.staffprofile.StaffProfileUpdateRequest;
import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.UserRole;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RoleRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
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
import java.util.Base64;
import java.util.Locale;

@Service
@Validated
@Transactional
public class StaffProfileService {

    private static final String STAFF_ROLE_CODE = "STAFF";

    private final StaffProfileRepository staffProfileRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public StaffProfileService(
            StaffProfileRepository staffProfileRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            EmailService emailService,
            Clock clock
    ) {
        this.staffProfileRepository = staffProfileRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.clock = clock;
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse hireStaff(@Valid StaffHireRequest request) {
        String normalizedEmployeeCode = normalizeUpper(request.employeeCode(), "Employee code");
        if (staffProfileRepository.findByEmployeeCodeIgnoreCase(normalizedEmployeeCode).isPresent()) {
            throw new DuplicateResourceException("StaffProfile", "employee code", normalizedEmployeeCode);
        }

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
                .orElseGet(() -> createStaffUser(request));
        if (staffProfileRepository.existsByUser_Id(user.getId())) {
            throw new DuplicateResourceException("StaffProfile", "user", user.getEmail());
        }
        assignStaffRoleIfMissing(user);

        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .employeeCode(normalizedEmployeeCode)
                .position(request.position().strip())
                .department(normalizeOptionalText(request.department()))
                .hiredAt(request.hiredAt() != null ? request.hiredAt() : LocalDate.now(clock))
                .employmentStatus(EmploymentStatus.ACTIVE)
                .baseSalary(request.baseSalary())
                .build();

        return mapResponse(staffProfileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse getStaff(String employeeCode) {
        return mapResponse(getExistingProfile(employeeCode));
    }

    @PreAuthorize(PermissionExpressions.STAFF_MANAGE)
    public StaffProfileResponse editStaff(String employeeCode, @Valid StaffProfileUpdateRequest request) {
        StaffProfile profile = getExistingProfile(employeeCode);

        if (request.position() != null) {
            String normalizedPosition = request.position().strip();
            if (normalizedPosition.isBlank()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "Position cannot be blank");
            }
            profile.setPosition(normalizedPosition);
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
        StaffProfile profile = getExistingProfile(employeeCode);
        profile.setEmploymentStatus(EmploymentStatus.TERMINATED);
        profile.setTerminatedAt(LocalDate.now(clock));
        staffProfileRepository.saveAndFlush(profile);
    }

    private User createStaffUser(StaffHireRequest request) {
        String rawPassword = generateTemporaryPassword();
        User user = User.builder()
                .publicId(java.util.UUID.randomUUID().toString())
                .email(normalizeEmail(request.email()))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .phone(normalizeOptionalText(request.phone()))
                .fullName(request.fullName().strip())
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(OffsetDateTime.now(clock))
                .failedLoginCount(0)
                .build();
        User savedUser = userRepository.save(user);

        AuthTokenService.IssuedAuthToken token =
                authTokenService.createToken(savedUser, AuthTokenType.PASSWORD_RESET, null);
        emailService.sendPasswordResetEmail(savedUser.getEmail(), token.value());

        return savedUser;
    }

    private void assignStaffRoleIfMissing(User user) {
        boolean alreadyHasStaffRole = user.getUserRoles().stream()
                .anyMatch(userRole -> STAFF_ROLE_CODE.equals(userRole.getRole().getCode()));
        if (alreadyHasStaffRole) {
            return;
        }
        Role staffRole = roleRepository.findByCode(STAFF_ROLE_CODE)
                .orElseThrow(() -> new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Role STAFF chưa được seed"));
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(staffRole)
                .assignedAt(OffsetDateTime.now(clock))
                .build();
        user.getUserRoles().add(userRole);
        userRepository.save(user);
    }

    private String generateTemporaryPassword() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private StaffProfile getExistingProfile(String employeeCode) {
        String normalizedEmployeeCode = normalizeUpper(employeeCode, "Employee code");
        return staffProfileRepository.findByEmployeeCodeIgnoreCase(normalizedEmployeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", normalizedEmployeeCode));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
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
                profile.getBaseSalary(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
