package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.customerprofile.CustomerProfileCreateRequest;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileResponse;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileUpdateRequest;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import jakarta.validation.Valid;
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

    public CustomerProfileService(
            CustomerProfileRepository customerProfileRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
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
                .city(normalizeOptionalText(request.city()))
                .country(normalizeOptionalText(request.country()))
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
            profile.setNationality(normalizeUpper(request.nationality()));
        }
        if (request.addressLine() != null) {
            profile.setAddressLine(normalizeOptionalText(request.addressLine()));
        }
        if (request.city() != null) {
            profile.setCity(normalizeOptionalText(request.city()));
        }
        if (request.country() != null) {
            profile.setCountry(normalizeOptionalText(request.country()));
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
                user.getFullName(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getNationality(),
                profile.getAddressLine(),
                profile.getCity(),
                profile.getCountry(),
                profile.getLoyaltyPoints(),
                profile.getTotalStays(),
                profile.getNotes(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
