package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.user.UserResponse;
import com.example.hotelmanagement.dto.user.UserUpdateRequest;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-08-19T08:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, FIXED_CLOCK);
    }

    @Test
    void getUsersReturnsNonDeletedUsers() {
        User user = createUser("public-id", "guest@example.com");
        when(userRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(user));

        List<UserResponse> users = userService.getUsers();

        assertThat(users).hasSize(1);
        assertThat(users.getFirst().publicId()).isEqualTo("public-id");
        assertThat(users.getFirst().email()).isEqualTo("guest@example.com");
    }

    @Test
    void updateUserAppliesPartialFields() {
        User user = createUser("public-id", "guest@example.com");
        when(userRepository.findByPublicIdAndDeletedAtIsNull("public-id")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneAndDeletedAtIsNull("+84901234567")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUser(
            "public-id",
            new UserUpdateRequest(" Nguyen Van B ", "+84901234567", " ", UserStatus.SUSPENDED)
        );

        assertThat(response.fullName()).isEqualTo("Nguyen Van B");
        assertThat(response.phone()).isEqualTo("+84901234567");
        assertThat(response.avatarUrl()).isNull();
        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void updateUserRejectsDuplicatePhone() {
        User user = createUser("public-id", "guest@example.com");
        User otherUser = createUser("other-public-id", "other@example.com");
        when(userRepository.findByPublicIdAndDeletedAtIsNull("public-id")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneAndDeletedAtIsNull("+84901234567")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> userService.updateUser(
            "public-id",
            new UserUpdateRequest(null, "+84901234567", null, null)
        )).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteUserSoftDeletesAndDeactivatesUser() {
        User user = createUser("public-id", "guest@example.com");
        when(userRepository.findByPublicIdAndDeletedAtIsNull("public-id")).thenReturn(Optional.of(user));

        userService.deleteUser("public-id");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(user.getDeletedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        verify(userRepository).save(user);
    }

    private User createUser(String publicId, String email) {
        return User.builder()
            .publicId(publicId)
            .email(email)
            .fullName("Guest")
            .status(UserStatus.ACTIVE)
            .failedLoginCount(0)
            .build();
    }
}
