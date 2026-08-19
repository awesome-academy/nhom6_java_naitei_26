package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.user.UserResponse;
import com.example.hotelmanagement.dto.user.UserUpdateRequest;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Validated
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()
            .stream()
            .map(this::mapUserResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String publicId) {
        return mapUserResponse(getExistingUser(publicId));
    }

    public UserResponse updateUser(String publicId, @Valid UserUpdateRequest request) {
        User user = getExistingUser(publicId);

        if (request.fullName() != null) {
            user.setFullName(normalizeRequiredText(request.fullName(), "Full name"));
        }
        if (request.phone() != null) {
            String normalizedPhone = normalizeOptionalText(request.phone());
            ensurePhoneIsAvailable(normalizedPhone, user);
            user.setPhone(normalizedPhone);
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalizeOptionalText(request.avatarUrl()));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        return mapUserResponse(userRepository.save(user));
    }

    public void deleteUser(String publicId) {
        User user = getExistingUser(publicId);
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeletedAt(OffsetDateTime.now(clock));
        userRepository.save(user);
    }

    private User getExistingUser(String publicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ResourceNotFoundException("User", publicId));
    }

    private void ensurePhoneIsAvailable(String phone, User user) {
        if (phone == null || phone.equals(user.getPhone())) {
            return;
        }
        userRepository.findByPhoneAndDeletedAtIsNull(phone)
            .filter(existingUser -> !existingUser.getPublicId().equals(user.getPublicId()))
            .ifPresent(existingUser -> {
                throw new DuplicateResourceException("User", "phone", phone);
            });
    }

    private UserResponse mapUserResponse(User user) {
        return new UserResponse(
            user.getPublicId(),
            user.getEmail(),
            user.getEmailVerifiedAt(),
            user.getPhone(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getStatus(),
            collectRoles(user),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private Set<String> collectRoles(User user) {
        Set<String> roles = new LinkedHashSet<>();
        user.getUserRoles().forEach(userRole -> roles.add(userRole.getRole().getCode()));
        return Set.copyOf(roles);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalizedValue = value.strip();
        if (normalizedValue.isBlank()) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return normalizedValue;
    }

    private String normalizeOptionalText(String value) {
        String normalizedValue = value.strip();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }
}
