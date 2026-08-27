package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.customerprofile.CustomerProfileCreateRequest;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileResponse;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileUpdateRequest;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@Validated
@Transactional
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final AvatarUrlResolver avatarUrlResolver;

    @Autowired
    public CustomerProfileService(
            CustomerProfileRepository customerProfileRepository,
            UserRepository userRepository,
            Clock clock,
            AvatarUrlResolver avatarUrlResolver
    ) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.avatarUrlResolver = avatarUrlResolver;
    }

    public CustomerProfileService(
            CustomerProfileRepository customerProfileRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this(customerProfileRepository, userRepository, clock, null);
    }

    public CustomerProfileResponse createOwnProfile(Long userId, @Valid CustomerProfileCreateRequest request) {
        if (customerProfileRepository.existsByUser_Id(userId)) {
            throw new DuplicateResourceException("CustomerProfile", "user", userId.toString());
        }
        User user = getExistingUser(userId);

        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .nationality(normalizeUpper(request.nationality()))
                .addressLine(normalizeOptionalText(request.addressLine()))
                .province(normalizeOptionalText(request.province()))
                .country(normalizeUpper(request.country()))
                .notes(normalizeOptionalText(request.notes()))
                .build();

        return mapResponse(customerProfileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getOwnProfile(Long userId) {
        return mapResponse(getExistingProfile(userId));
    }

    public CustomerProfileResponse updateOwnProfile(Long userId, @Valid CustomerProfileUpdateRequest request) {
        CustomerProfile profile = getExistingProfile(userId);

        if (request.dateOfBirth() != null) {
            profile.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            profile.setGender(request.gender());
        }
        if (request.nationality() != null) {
            if (request.nationality().isBlank()) {
                profile.setNationality(null);
            } else {
                profile.setNationality(normalizeUpper(request.nationality()));
            }
        }
        if (request.addressLine() != null) {
            profile.setAddressLine(normalizeOptionalText(request.addressLine()));
        }
        if (request.province() != null) {
            if (request.province().isBlank()) {
                profile.setProvince(null);
            } else {
                profile.setProvince(normalizeOptionalText(request.province()));
            }
        }
        if (request.country() != null) {
            if (request.country().isBlank()) {
                profile.setCountry(null);
            } else {
                profile.setCountry(normalizeUpper(request.country()));
            }
        }
        if (request.notes() != null) {
            profile.setNotes(normalizeOptionalText(request.notes()));
        }

        return mapResponse(customerProfileRepository.saveAndFlush(profile));
    }

    public void deactivateOwnAccount(Long userId) {
        CustomerProfile profile = getExistingProfile(userId);
        User user = profile.getUser();
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeletedAt(OffsetDateTime.now(clock));
        userRepository.save(user);
    }

    private CustomerProfile getExistingProfile(Long userId) {
        return customerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerProfile", userId.toString()));
    }

    private User getExistingUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.strip();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }

    private CustomerProfileResponse mapResponse(CustomerProfile profile) {
        User user = profile.getUser();
        return new CustomerProfileResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getNationality(),
                profile.getProvince(),
                profile.getAddressLine(),
                profile.getCountry(),
                avatarUrlResolver == null ? user.getAvatarUrl() : avatarUrlResolver.resolve(user),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt(),
                profile.getLoyaltyPoints(),
                profile.getTotalStays(),
                profile.getNotes(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
