package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.dto.roomimage.RoomImageConfirmRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageOrderRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlResponse;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomImage;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.StorageObjectNotFoundException;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import com.example.hotelmanagement.repositories.RoomImageRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Validated
@Transactional
public class RoomImageService {

    private static final Logger log = LoggerFactory.getLogger(RoomImageService.class);
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS;

    static {
        Map<String, String> supportedTypes = new LinkedHashMap<>();
        supportedTypes.put("image/jpeg", "jpg");
        supportedTypes.put("image/png", "png");
        supportedTypes.put("image/webp", "webp");
        CONTENT_TYPE_EXTENSIONS = Collections.unmodifiableMap(supportedTypes);
    }

    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final RoomImageStorage roomImageStorage;
    private final MinioProperties properties;

    public RoomImageService(
            RoomRepository roomRepository,
            RoomImageRepository roomImageRepository,
            RoomImageStorage roomImageStorage,
            MinioProperties properties
    ) {
        this.roomRepository = roomRepository;
        this.roomImageRepository = roomImageRepository;
        this.roomImageStorage = roomImageStorage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomImageUploadUrlResponse createUploadUrl(
            String roomNumber,
            @Valid RoomImageUploadUrlRequest request
    ) {
        Room room = getExistingRoom(roomNumber);
        validateImageLimit(room);
        String contentType = normalizeContentType(request.contentType());
        validateFileName(request.fileName(), contentType);
        validateImageSize(request.fileSize());

        UUID uploadId = UUID.randomUUID();
        String objectKey = buildObjectKey(room, uploadId, contentType);
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
            String roomNumber,
            @Valid RoomImageConfirmRequest request
    ) {
        Room room = getExistingRoomForUpdate(roomNumber);
        UploadedObject uploadedObject = findUploadedObject(room, request.uploadId());
        String objectKey = uploadedObject.objectKey();
        if (roomImageRepository.existsByRoomIdAndStorageKey(room.getId(), objectKey)) {
            throw new DuplicateResourceException("Room image", "upload ID", request.uploadId().toString());
        }
        try {
            validateImageLimit(room);
        } catch (BusinessValidationException exception) {
            log.warn("Uploaded room image exceeds room limit roomId={} uploadId={}",
                    room.getId(), request.uploadId());
            roomImageStorage.deleteObjectBestEffort(objectKey);
            throw exception;
        }

        try {
            validateUploadedObject(uploadedObject.metadata(), uploadedObject.contentType());
        } catch (BusinessValidationException exception) {
            log.warn("Uploaded room image failed validation roomId={} uploadId={}",
                    room.getId(), request.uploadId());
            roomImageStorage.deleteObjectBestEffort(objectKey);
            throw exception;
        }

        List<RoomImage> currentImages = getSortedImages(room);
        RoomImage roomImage = RoomImage.builder()
                .room(room)
                .url(roomImageStorage.getObjectUri(objectKey))
                .storageKey(objectKey)
                .altText(request.altText().strip())
                .isPrimary(currentImages.isEmpty())
                .sortOrder(currentImages.size())
                .build();
        RoomImage savedImage;
        try {
            savedImage = roomImageRepository.saveAndFlush(roomImage);
        } catch (RuntimeException exception) {
            log.error("Failed to persist room image roomId={} uploadId={}",
                    room.getId(), request.uploadId(), exception);
            roomImageStorage.deleteObjectBestEffort(objectKey);
            throw exception;
        }
        return mapRoomImageResponse(savedImage);
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public List<RoomImageResponse> reorderImages(
            String roomNumber,
            @Valid RoomImageOrderRequest request
    ) {
        Room room = getExistingRoomForUpdate(roomNumber);
        List<RoomImage> currentImages = getSortedImages(room);
        Map<UUID, RoomImage> imagesById = mapImagesByPublicId(currentImages);
        if (request.imageIds().size() > properties.maxImagesPerRoom()) {
            throw new BusinessValidationException(
                    "Image order cannot contain more than " + properties.maxImagesPerRoom() + " images"
            );
        }
        Set<UUID> requestedIds = new LinkedHashSet<>(request.imageIds());
        if (requestedIds.size() != request.imageIds().size()) {
            throw new BusinessValidationException("Image order cannot contain duplicate image IDs");
        }
        if (!requestedIds.equals(imagesById.keySet())) {
            throw new BusinessValidationException("Image order must contain every room image exactly once");
        }

        List<RoomImage> reorderedImages = new ArrayList<>();
        for (int index = 0; index < request.imageIds().size(); index++) {
            RoomImage image = imagesById.get(request.imageIds().get(index));
            image.setSortOrder(index);
            image.setIsPrimary(index == 0);
            reorderedImages.add(image);
        }
        return roomImageRepository.saveAllAndFlush(reorderedImages)
                .stream()
                .sorted(Comparator.comparing(RoomImage::getSortOrder))
                .map(this::mapRoomImageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomImageResponse> getRoomImageResponses(Room room) {
        return getSortedImages(room)
                .stream()
                .map(this::mapRoomImageResponse)
                .toList();
    }

    private void validateFileName(String fileName, String contentType) {
        String normalizedFileName = fileName.strip().toLowerCase(Locale.ROOT);
        int extensionSeparator = normalizedFileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == normalizedFileName.length() - 1) {
            throw new BusinessValidationException("Image file name must include a supported extension");
        }
        String extension = normalizedFileName.substring(extensionSeparator + 1);
        boolean isMatchingExtension = switch (contentType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
        if (!isMatchingExtension) {
            throw new BusinessValidationException("Image file extension does not match its content type");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalizedContentType = contentType.strip().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPE_EXTENSIONS.containsKey(normalizedContentType)) {
            throw new BusinessValidationException("Only JPEG, PNG and WebP room images are supported");
        }
        return normalizedContentType;
    }

    private void validateImageSize(long fileSize) {
        if (fileSize > properties.maxImageSizeBytes()) {
            throw new BusinessValidationException(
                    "Room image must not exceed " + properties.maxImageSizeBytes() + " bytes"
            );
        }
    }

    private void validateUploadedObject(
            RoomImageStorage.StoredObject object,
            String expectedContentType
    ) {
        if (object.size() < 1 || object.size() > properties.maxImageSizeBytes()) {
            throw new BusinessValidationException("Uploaded room image has an invalid size");
        }
        String actualContentType = object.contentType() == null
                ? ""
                : object.contentType().toLowerCase(Locale.ROOT);
        if (!expectedContentType.equals(actualContentType)) {
            throw new BusinessValidationException("Uploaded room image content type does not match the request");
        }
    }

    private void validateImageLimit(Room room) {
        if (roomImageRepository.countByRoomId(room.getId()) >= properties.maxImagesPerRoom()) {
            throw new BusinessValidationException(
                    "A room can contain at most " + properties.maxImagesPerRoom() + " images"
            );
        }
    }

    private Room getExistingRoom(String roomNumber) {
        String normalizedRoomNumber = normalizeRoomNumber(roomNumber);
        return roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull(normalizedRoomNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Room", normalizedRoomNumber));
    }

    private Room getExistingRoomForUpdate(String roomNumber) {
        String normalizedRoomNumber = normalizeRoomNumber(roomNumber);
        return roomRepository.findForUpdateByRoomNumber(normalizedRoomNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Room", normalizedRoomNumber));
    }

    private String normalizeRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new BusinessValidationException("Room number cannot be blank");
        }
        return roomNumber.strip().toUpperCase(Locale.ROOT);
    }

    private String buildObjectKey(Room room, UUID uploadId, String contentType) {
        return "rooms/" + room.getId() + "/" + uploadId + "." + CONTENT_TYPE_EXTENSIONS.get(contentType);
    }

    private UploadedObject findUploadedObject(Room room, UUID uploadId) {
        for (Map.Entry<String, String> supportedType : CONTENT_TYPE_EXTENSIONS.entrySet()) {
            String contentType = supportedType.getKey();
            String objectKey = buildObjectKey(room, uploadId, contentType);
            try {
                return new UploadedObject(
                        objectKey,
                        contentType,
                        roomImageStorage.getObjectMetadata(objectKey)
                );
            } catch (StorageObjectNotFoundException exception) {
                log.debug("Room image candidate was not found roomId={} uploadId={} extension={}",
                        room.getId(), uploadId, supportedType.getValue());
            }
        }
        throw new ResourceNotFoundException("Uploaded room image", uploadId.toString());
    }

    private List<RoomImage> getSortedImages(Room room) {
        return room.getImages()
                .stream()
                .sorted(Comparator.comparing(RoomImage::getSortOrder).thenComparing(RoomImage::getId))
                .toList();
    }

    private Map<UUID, RoomImage> mapImagesByPublicId(List<RoomImage> images) {
        Map<UUID, RoomImage> imagesById = new HashMap<>();
        for (RoomImage image : images) {
            UUID imageId = extractImageId(image);
            if (imagesById.put(imageId, image) != null) {
                log.error("Duplicate derived room image ID roomId={} imageId={}",
                        image.getRoom().getId(), imageId);
                throw new StorageUnavailableException("Room image metadata is inconsistent");
            }
        }
        return imagesById;
    }

    private RoomImageResponse mapRoomImageResponse(RoomImage image) {
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

    private UUID extractImageId(RoomImage image) {
        String storageKey = image.getStorageKey();
        int pathSeparator = storageKey == null ? -1 : storageKey.lastIndexOf('/');
        int extensionSeparator = storageKey == null ? -1 : storageKey.lastIndexOf('.');
        if (pathSeparator < 0 || extensionSeparator <= pathSeparator + 1) {
            log.error("Invalid room image storage key roomImageId={}", image.getId());
            throw new StorageUnavailableException("Room image metadata is inconsistent");
        }
        try {
            return UUID.fromString(storageKey.substring(pathSeparator + 1, extensionSeparator));
        } catch (IllegalArgumentException exception) {
            log.error("Invalid room image UUID roomImageId={}", image.getId(), exception);
            throw new StorageUnavailableException("Room image metadata is inconsistent", exception);
        }
    }

    private record UploadedObject(
            String objectKey,
            String contentType,
            RoomImageStorage.StoredObject metadata
    ) {
    }
}
