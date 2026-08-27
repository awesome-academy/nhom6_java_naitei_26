package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.dto.avatar.AvatarConfirmRequest;
import com.example.hotelmanagement.dto.avatar.AvatarResponse;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlRequest;
import com.example.hotelmanagement.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTest {

    @Mock
    private com.example.hotelmanagement.repositories.UserRepository userRepository;
    @Mock
    private AvatarStorage avatarStorage;

    private UserAvatarService service;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties(
                "http://localhost:9000",
                "access",
                "secret",
                "room-images-legacy",
                "room-type-images",
                "avatars",
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                10 * 1024 * 1024,
                20,
                "invoices",
                Duration.ofHours(1)
        );
        service = new UserAvatarService(userRepository, avatarStorage, properties);
    }

    @Test
    void createAdminUploadUrlUsesUserScopedObjectKey() {
        User user = user("user-public-id", 7L);
        when(userRepository.findByPublicIdAndDeletedAtIsNull("user-public-id"))
                .thenReturn(Optional.of(user));
        when(avatarStorage.createUploadUrl(startsWith("avatars/users/7/"), eq("image/jpeg")))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://upload.example",
                        java.util.Map.of("Content-Type", "image/jpeg"),
                        OffsetDateTime.now().plusHours(1)
                ));

        var response = service.createAdminUploadUrl(
                "user-public-id",
                new AvatarUploadUrlRequest("avatar.jpg", "image/jpeg", 1024)
        );

        assertThat(response.uploadUrl()).isEqualTo("https://upload.example");
        verify(avatarStorage).createUploadUrl(startsWith("avatars/users/7/"), eq("image/jpeg"));
    }

    @Test
    void createCustomerUploadUrlUsesAuthenticatedUserScopedObjectKey() {
        User user = user("user-public-id", 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(avatarStorage.createUploadUrl(startsWith("avatars/users/7/"), eq("image/jpeg")))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://upload.example",
                        java.util.Map.of("Content-Type", "image/jpeg"),
                        OffsetDateTime.now().plusHours(1)
                ));

        var response = service.createCustomerUploadUrl(
                7L,
                new AvatarUploadUrlRequest("avatar.jpg", "image/jpeg", 1024)
        );

        assertThat(response.uploadUrl()).isEqualTo("https://upload.example");
        verify(avatarStorage).createUploadUrl(startsWith("avatars/users/7/"), eq("image/jpeg"));
    }

    @Test
    void confirmUploadReplacesAvatarAndCleansPreviousObject() {
        UUID uploadId = UUID.randomUUID();
        User user = user("user-public-id", 7L);
        user.setAvatarStorageKey("avatars/users/7/old.png");
        when(userRepository.findByPublicIdAndDeletedAtIsNull("user-public-id"))
                .thenReturn(Optional.of(user));
        when(avatarStorage.getObjectMetadata(startsWith("avatars/users/7/")))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (!key.endsWith(".png")) {
                        throw new com.example.hotelmanagement.exceptions.StorageObjectNotFoundException(
                                "not found", new RuntimeException()
                        );
                    }
                    return new RoomImageStorage.StoredObject(1024, "image/png");
                });
        when(avatarStorage.getObjectUri(startsWith("avatars/users/7/")))
                .thenAnswer(invocation -> "minio://avatars/" + invocation.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(avatarStorage.createDownloadUrl(startsWith("avatars/users/7/")))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://download.example/avatar.png",
                        java.util.Map.of(),
                        OffsetDateTime.now().plusMinutes(15)
                ));

        AvatarResponse response = service.confirmAdminUpload(
                "user-public-id",
                new AvatarConfirmRequest(uploadId)
        );

        assertThat(response.avatarUrl()).isEqualTo("https://download.example/avatar.png");
        assertThat(user.getAvatarStorageKey()).startsWith("avatars/users/7/");
        assertThat(user.getAvatarContentType()).isEqualTo("image/png");
        verify(avatarStorage).deleteObjectBestEffort("avatars/users/7/old.png");
    }

    @Test
    void rejectsUnsupportedAvatarType() {
        User user = user("user-public-id", 7L);
        when(userRepository.findByPublicIdAndDeletedAtIsNull("user-public-id"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createAdminUploadUrl(
                "user-public-id",
                new AvatarUploadUrlRequest("avatar.gif", "image/gif", 1024)
        )).isInstanceOf(com.example.hotelmanagement.exceptions.BusinessValidationException.class);
    }

    private User user(String publicId, Long id) {
        User user = User.builder()
                .publicId(publicId)
                .email("user@example.com")
                .fullName("User")
                .userRoles(new HashSet<>())
                .build();
        user.setId(id);
        return user;
    }
}
