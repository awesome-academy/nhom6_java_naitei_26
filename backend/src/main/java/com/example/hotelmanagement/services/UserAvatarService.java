package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.dto.avatar.AvatarConfirmRequest;
import com.example.hotelmanagement.dto.avatar.AvatarResponse;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlRequest;
import com.example.hotelmanagement.dto.avatar.AvatarUploadUrlResponse;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.StorageObjectNotFoundException;
import com.example.hotelmanagement.repositories.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Validated
@Transactional
public class UserAvatarService {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarService.class);
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS;

    static {
        Map<String, String> supportedTypes = new LinkedHashMap<>();
        supportedTypes.put("image/jpeg", "jpg");
        supportedTypes.put("image/png", "png");
        supportedTypes.put("image/webp", "webp");
        CONTENT_TYPE_EXTENSIONS = Collections.unmodifiableMap(supportedTypes);
    }

    private final UserRepository userRepository;
    private final AvatarStorage avatarStorage;
    private final MinioProperties properties;

    public UserAvatarService(
            UserRepository userRepository,
            AvatarStorage avatarStorage,
            MinioProperties properties
    ) {
        this.userRepository = userRepository;
        this.avatarStorage = avatarStorage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AvatarUploadUrlResponse createAdminUploadUrl(
            String publicId,
            @Valid AvatarUploadUrlRequest request
    ) {
        return createUploadUrl(getExistingUser(publicId), request, "users");
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AvatarResponse confirmAdminUpload(
            String publicId,
            @Valid AvatarConfirmRequest request
    ) {
        return confirmUpload(getExistingUser(publicId), request, "users");
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CUSTOMER')")
    public AvatarUploadUrlResponse createCustomerUploadUrl(
            Long userId,
            @Valid AvatarUploadUrlRequest request
    ) {
        return createUploadUrl(getExistingUser(userId), request, "users");
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public AvatarResponse confirmCustomerUpload(
            Long userId,
            @Valid AvatarConfirmRequest request
    ) {
        return confirmUpload(getExistingUser(userId), request, "users");
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STAFF')")
    public AvatarUploadUrlResponse createStaffUploadUrl(
            Long userId,
            @Valid AvatarUploadUrlRequest request
    ) {
        return createUploadUrl(getExistingStaff(userId), request, "staff");
    }

    @PreAuthorize("hasRole('STAFF')")
    public AvatarResponse confirmStaffUpload(
            Long userId,
            @Valid AvatarConfirmRequest request
    ) {
        return confirmUpload(getExistingStaff(userId), request, "staff");
    }

    private AvatarUploadUrlResponse createUploadUrl(
            User user,
            AvatarUploadUrlRequest request,
            String ownerFolder
    ) {
        String contentType = normalizeContentType(request.contentType());
        validateFileName(request.fileName(), contentType);
        validateImageSize(request.fileSize());

        UUID uploadId = UUID.randomUUID();
        String objectKey = buildObjectKey(user, ownerFolder, uploadId, contentType);
        RoomImageStorage.PresignedUrl presignedUrl = avatarStorage.createUploadUrl(objectKey, contentType);
        return new AvatarUploadUrlResponse(
                uploadId,
                presignedUrl.url(),
                presignedUrl.requiredHeaders(),
                presignedUrl.expiresAt()
        );
    }

    private AvatarResponse confirmUpload(
            User user,
            AvatarConfirmRequest request,
            String ownerFolder
    ) {
        UploadedObject uploadedObject = findUploadedObject(user, ownerFolder, request.uploadId());
        String objectKey = uploadedObject.objectKey();
        validateUploadedObject(uploadedObject.metadata(), uploadedObject.contentType());

        String previousStorageKey = user.getAvatarStorageKey();
        user.setAvatarUrl(avatarStorage.getObjectUri(objectKey));
        user.setAvatarStorageKey(objectKey);
        user.setAvatarContentType(uploadedObject.contentType());
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (RuntimeException exception) {
            log.error("Failed to persist avatar userId={} uploadId={}", user.getId(), request.uploadId(), exception);
            avatarStorage.deleteObjectBestEffort(objectKey);
            throw exception;
        }

        if (previousStorageKey != null && !previousStorageKey.equals(objectKey)) {
            avatarStorage.deleteObjectBestEffort(previousStorageKey);
        }
        RoomImageStorage.PresignedUrl downloadUrl = avatarStorage.createDownloadUrl(objectKey);
        return new AvatarResponse(
                savedUser.getPublicId(),
                downloadUrl.url(),
                downloadUrl.expiresAt()
        );
    }

    private User getExistingUser(String publicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("User", publicId));
    }

    private User getExistingUser(Long userId) {
        return userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private User getExistingStaff(Long userId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user", userId.toString()));
        boolean isStaff = user.getUserRoles().stream()
                .anyMatch(userRole -> "STAFF".equals(userRole.getRole().getCode()));
        if (!isStaff) {
            throw new ResourceNotFoundException("Staff user", userId.toString());
        }
        return user;
    }

    private String normalizeContentType(String contentType) {
        String normalizedContentType = contentType.strip().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPE_EXTENSIONS.containsKey(normalizedContentType)) {
            throw new BusinessValidationException("Only JPEG, PNG and WebP avatars are supported");
        }
        return normalizedContentType;
    }

    private void validateFileName(String fileName, String contentType) {
        String normalizedFileName = fileName.strip().toLowerCase(Locale.ROOT);
        int extensionSeparator = normalizedFileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == normalizedFileName.length() - 1) {
            throw new BusinessValidationException("Avatar file name must include a supported extension");
        }
        String extension = normalizedFileName.substring(extensionSeparator + 1);
        boolean matchesContentType = switch (contentType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
        if (!matchesContentType) {
            throw new BusinessValidationException("Avatar file extension does not match its content type");
        }
    }

    private void validateImageSize(long fileSize) {
        if (fileSize > properties.maxImageSizeBytes()) {
            throw new BusinessValidationException(
                    "Avatar must not exceed " + properties.maxImageSizeBytes() + " bytes"
            );
        }
    }

    private void validateUploadedObject(RoomImageStorage.StoredObject object, String expectedContentType) {
        if (object.size() < 1 || object.size() > properties.maxImageSizeBytes()) {
            throw new BusinessValidationException("Uploaded avatar has an invalid size");
        }
        String actualContentType = object.contentType() == null
                ? ""
                : object.contentType().toLowerCase(Locale.ROOT);
        if (!expectedContentType.equals(actualContentType)) {
            throw new BusinessValidationException("Uploaded avatar content type does not match the request");
        }
    }

    private UploadedObject findUploadedObject(User user, String ownerFolder, UUID uploadId) {
        for (Map.Entry<String, String> supportedType : CONTENT_TYPE_EXTENSIONS.entrySet()) {
            String contentType = supportedType.getKey();
            String objectKey = buildObjectKey(user, ownerFolder, uploadId, contentType);
            try {
                return new UploadedObject(
                        objectKey,
                        contentType,
                        avatarStorage.getObjectMetadata(objectKey)
                );
            } catch (StorageObjectNotFoundException exception) {
                log.debug("Avatar candidate was not found userId={} uploadId={} extension={}",
                        user.getId(), uploadId, supportedType.getValue());
            }
        }
        throw new ResourceNotFoundException("Uploaded avatar", uploadId.toString());
    }

    private String buildObjectKey(User user, String ownerFolder, UUID uploadId, String contentType) {
        return "avatars/" + ownerFolder + "/" + user.getId() + "/" + uploadId + "."
                + CONTENT_TYPE_EXTENSIONS.get(contentType);
    }

    private record UploadedObject(
            String objectKey,
            String contentType,
            RoomImageStorage.StoredObject metadata
    ) {
    }
}
