package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.dto.roomimage.RoomImageConfirmRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlResponse;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.RoomTypeImage;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.StorageObjectNotFoundException;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import com.example.hotelmanagement.repositories.RoomTypeImageRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Validated
@Transactional
public class RoomTypeImageService {

    private static final Logger log = LoggerFactory.getLogger(RoomTypeImageService.class);
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS;

    static {
        Map<String, String> supportedTypes = new LinkedHashMap<>();
        supportedTypes.put("image/jpeg", "jpg");
        supportedTypes.put("image/png", "png");
        supportedTypes.put("image/webp", "webp");
        CONTENT_TYPE_EXTENSIONS = Collections.unmodifiableMap(supportedTypes);
    }

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomImageStorage roomImageStorage;
    private final MinioProperties properties;

    public RoomTypeImageService(
            RoomTypeRepository roomTypeRepository,
            RoomTypeImageRepository roomTypeImageRepository,
            @Qualifier("roomTypeImageStorage") RoomImageStorage roomImageStorage,
            MinioProperties properties
    ) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomTypeImageRepository = roomTypeImageRepository;
        this.roomImageStorage = roomImageStorage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomImageUploadUrlResponse createUploadUrl(
            String roomTypeCode,
            @Valid RoomImageUploadUrlRequest request
    ) {
        RoomType roomType = getExistingRoomType(roomTypeCode);
        validateImageLimit(roomType);
        String contentType = normalizeContentType(request.contentType());
        validateFileName(request.fileName(), contentType);
        validateImageSize(request.fileSize());

        UUID uploadId = UUID.randomUUID();
        String objectKey = buildObjectKey(roomType, uploadId, contentType);
        RoomImageStorage.PresignedUrl presignedUrl = roomImageStorage.createUploadUrl(
                objectKey,
                contentType
        );
        return new RoomImageUploadUrlResponse(
                uploadId,
                presignedUrl.url(),
                presignedUrl.requiredHeaders(),
                presignedUrl.expiresAt()
        );
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomImageResponse confirmUpload(
            String roomTypeCode,
            @Valid RoomImageConfirmRequest request
    ) {
        RoomType roomType = getExistingRoomTypeForUpdate(roomTypeCode);
        UploadedObject uploadedObject = findUploadedObject(roomType, request.uploadId());
        String objectKey = uploadedObject.objectKey();
        if (roomTypeImageRepository.existsByRoomTypeIdAndStorageKey(roomType.getId(), objectKey)) {
            throw new DuplicateResourceException(
                    "Room type image",
                    "upload ID",
                    request.uploadId().toString()
            );
        }
        try {
            validateImageLimit(roomType);
            validateUploadedObject(uploadedObject.metadata(), uploadedObject.contentType());
        } catch (BusinessValidationException exception) {
            log.warn("Uploaded room type image failed validation roomTypeId={} uploadId={}",
                    roomType.getId(), request.uploadId());
            roomImageStorage.deleteObjectBestEffort(objectKey);
            throw exception;
        }

        List<RoomTypeImage> currentImages = getSortedImages(roomType);
        RoomTypeImage image = RoomTypeImage.builder()
                .roomType(roomType)
                .url(roomImageStorage.getObjectUri(objectKey))
                .storageKey(objectKey)
                .altText(request.altText().strip())
                .isPrimary(currentImages.isEmpty())
                .sortOrder(currentImages.size())
                .build();
        RoomTypeImage savedImage;
        try {
            savedImage = roomTypeImageRepository.saveAndFlush(image);
        } catch (RuntimeException exception) {
            log.error("Failed to persist room type image roomTypeId={} uploadId={}",
                    roomType.getId(), request.uploadId(), exception);
            roomImageStorage.deleteObjectBestEffort(objectKey);
            throw exception;
        }
        return mapImageResponse(savedImage);
    }

    @Transactional(readOnly = true)
    public List<RoomImageResponse> getImageResponses(RoomType roomType) {
        return getSortedImages(roomType).stream()
                .map(this::mapImageResponse)
                .toList();
    }

    private RoomType getExistingRoomType(String code) {
        String normalizedCode = normalizeCode(code);
        return roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", normalizedCode));
    }

    private RoomType getExistingRoomTypeForUpdate(String code) {
        String normalizedCode = normalizeCode(code);
        return roomTypeRepository.findForUpdateByCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", normalizedCode));
    }

    private void validateFileName(String fileName, String contentType) {
        String normalizedFileName = fileName.strip().toLowerCase(Locale.ROOT);
        int extensionSeparator = normalizedFileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == normalizedFileName.length() - 1) {
            throw new BusinessValidationException("Image file name must include a supported extension");
        }
        String extension = normalizedFileName.substring(extensionSeparator + 1);
        boolean matchesContentType = switch (contentType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
        if (!matchesContentType) {
            throw new BusinessValidationException("Image file extension does not match its content type");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalizedContentType = contentType.strip().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPE_EXTENSIONS.containsKey(normalizedContentType)) {
            throw new BusinessValidationException("Only JPEG, PNG and WebP room type images are supported");
        }
        return normalizedContentType;
    }

    private void validateImageSize(long fileSize) {
        if (fileSize > properties.maxImageSizeBytes()) {
            throw new BusinessValidationException(
                    "Room type image must not exceed " + properties.maxImageSizeBytes() + " bytes"
            );
        }
    }

    private void validateImageLimit(RoomType roomType) {
        if (roomTypeImageRepository.countByRoomTypeId(roomType.getId()) >= properties.maxImagesPerRoom()) {
            throw new BusinessValidationException(
                    "A room type can contain at most " + properties.maxImagesPerRoom() + " images"
            );
        }
    }

    private void validateUploadedObject(
            RoomImageStorage.StoredObject object,
            String expectedContentType
    ) {
        if (object.size() < 1 || object.size() > properties.maxImageSizeBytes()) {
            throw new BusinessValidationException("Uploaded room type image has an invalid size");
        }
        String actualContentType = object.contentType() == null
                ? ""
                : object.contentType().toLowerCase(Locale.ROOT);
        if (!expectedContentType.equals(actualContentType)) {
            throw new BusinessValidationException("Uploaded room type image content type does not match the request");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Room type code cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private String buildObjectKey(RoomType roomType, UUID uploadId, String contentType) {
        return "room-types/" + roomType.getId() + "/" + uploadId + "."
                + CONTENT_TYPE_EXTENSIONS.get(contentType);
    }

    private UploadedObject findUploadedObject(RoomType roomType, UUID uploadId) {
        for (Map.Entry<String, String> supportedType : CONTENT_TYPE_EXTENSIONS.entrySet()) {
            String contentType = supportedType.getKey();
            String objectKey = buildObjectKey(roomType, uploadId, contentType);
            try {
                return new UploadedObject(
                        objectKey,
                        contentType,
                        roomImageStorage.getObjectMetadata(objectKey)
                );
            } catch (StorageObjectNotFoundException exception) {
                log.debug("Room type image candidate was not found roomTypeId={} uploadId={} extension={}",
                        roomType.getId(), uploadId, supportedType.getValue());
            }
        }
        throw new ResourceNotFoundException("Uploaded room type image", uploadId.toString());
    }

    private List<RoomTypeImage> getSortedImages(RoomType roomType) {
        return roomType.getImages().stream()
                .sorted(Comparator.comparing(RoomTypeImage::getSortOrder).thenComparing(RoomTypeImage::getId))
                .toList();
    }

    private RoomImageResponse mapImageResponse(RoomTypeImage image) {
        RoomImageStorage.PresignedUrl downloadUrl = roomImageStorage.createDownloadUrl(image.getStorageKey());
        return new RoomImageResponse(
                extractImageId(image),
                downloadUrl.url(),
                downloadUrl.expiresAt(),
                image.getAltText(),
                image.getIsPrimary(),
                image.getSortOrder()
        );
    }

    private UUID extractImageId(RoomTypeImage image) {
        String storageKey = image.getStorageKey();
        int pathSeparator = storageKey == null ? -1 : storageKey.lastIndexOf('/');
        int extensionSeparator = storageKey == null ? -1 : storageKey.lastIndexOf('.');
        if (pathSeparator < 0 || extensionSeparator <= pathSeparator + 1) {
            log.error("Invalid room type image storage key roomTypeImageId={}", image.getId());
            throw new StorageUnavailableException("Room type image metadata is inconsistent");
        }
        try {
            return UUID.fromString(storageKey.substring(pathSeparator + 1, extensionSeparator));
        } catch (IllegalArgumentException exception) {
            log.error("Invalid room type image UUID roomTypeImageId={}", image.getId(), exception);
            throw new StorageUnavailableException("Room type image metadata is inconsistent", exception);
        }
    }

    private record UploadedObject(
            String objectKey,
            String contentType,
            RoomImageStorage.StoredObject metadata
    ) {
    }
}
