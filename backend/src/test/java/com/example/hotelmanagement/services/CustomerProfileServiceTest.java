package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.customerprofile.CustomerProfileCreateRequest;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileResponse;
import com.example.hotelmanagement.dto.customerprofile.CustomerProfileUpdateRequest;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.Gender;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.CustomerProfileRepository;
import com.example.hotelmanagement.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private UserRepository userRepository;

    private CustomerProfileService customerProfileService;

    @BeforeEach
    void setUp() {
        customerProfileService = new CustomerProfileService(customerProfileRepository, userRepository, FIXED_CLOCK);
    }

    @Test
    void createOwnProfileBuildsProfileForCurrentUser() {
        User user = createUser(1L, "public-id", "guest@example.com");
        when(customerProfileRepository.existsByUser_Id(1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(customerProfileRepository.saveAndFlush(any(CustomerProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerProfileCreateRequest request = new CustomerProfileCreateRequest(
                LocalDate.of(1995, 1, 1), Gender.FEMALE, "vn", " 123 Le Loi ", " Da Nang ", " Vietnam ", " VIP "
        );

        CustomerProfileResponse response = customerProfileService.createOwnProfile(1L, request);

        ArgumentCaptor<CustomerProfile> captor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).saveAndFlush(captor.capture());
        CustomerProfile saved = captor.getValue();
        assertThat(saved.getNationality()).isEqualTo("VN");
        assertThat(saved.getAddressLine()).isEqualTo("123 Le Loi");
        assertThat(saved.getCity()).isEqualTo("Da Nang");
        assertThat(response.userPublicId()).isEqualTo("public-id");
        assertThat(response.email()).isEqualTo("guest@example.com");
    }

    @Test
    void createOwnProfileRejectsWhenProfileAlreadyExists() {
        when(customerProfileRepository.existsByUser_Id(1L)).thenReturn(true);

        CustomerProfileCreateRequest request = new CustomerProfileCreateRequest(
                null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> customerProfileService.createOwnProfile(1L, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateOwnProfileAppliesOnlyProvidedFieldsAndClearsBlankValues() {
        User user = createUser(1L, "public-id", "guest@example.com");
        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .city("Old City")
                .notes("Old note")
                .build();
        when(customerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));
        when(customerProfileRepository.saveAndFlush(any(CustomerProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerProfileUpdateRequest request = new CustomerProfileUpdateRequest(
                null, null, null, null, "New City", null, "   "
        );
        CustomerProfileResponse response = customerProfileService.updateOwnProfile(1L, request);

        assertThat(response.city()).isEqualTo("New City");
        assertThat(response.notes()).isNull();
    }

    @Test
    void getOwnProfileThrowsWhenNotFound() {
        when(customerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerProfileService.getOwnProfile(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateOwnAccountDeactivatesUnderlyingUser() {
        User user = createUser(1L, "public-id", "guest@example.com");
        CustomerProfile profile = CustomerProfile.builder().user(user).build();
        when(customerProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(profile));

        customerProfileService.deactivateOwnAccount(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(user.getDeletedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        verify(userRepository).save(user);
    }

    private User createUser(Long id, String publicId, String email) {
        User user = User.builder()
                .publicId(publicId)
                .email(email)
                .fullName("Guest")
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        user.setId(id);
        return user;
    }
}
