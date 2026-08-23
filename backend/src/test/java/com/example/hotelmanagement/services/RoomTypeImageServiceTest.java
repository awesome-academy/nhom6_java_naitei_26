package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.dto.roomimage.RoomImageConfirmRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlRequest;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.RoomTypeImage;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.StorageObjectNotFoundException;
import com.example.hotelmanagement.repositories.RoomTypeImageRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomTypeImageServiceTest {

    private static final OffsetDateTime EXPIRY = OffsetDateTime.of(
            2026, 8, 20, 10, 0, 0, 0, ZoneOffset.UTC
    );

    @Mock
    private RoomTypeRepository roomTypeRepository;
    @Mock
    private RoomTypeImageRepository roomTypeImageRepository;
    @Mock
    private RoomImageStorage roomImageStorage;

    private RoomTypeImageService service;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties(
                "http://localhost:9000",
                "test-access",
                "test-secret",
                "room-images",
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                10 * 1024 * 1024,
                20,
                "invoices",
                Duration.ofHours(1)
        );
        service = new RoomTypeImageService(
                roomTypeRepository,
                roomTypeImageRepository,
                roomImageStorage,
                properties
        );
        roomType = RoomType.builder().code("DLX").name("Deluxe").build();
        roomType.setId(15L);
    }

    @Test
    void createUploadUrlUsesRoomTypeIsolatedObjectKey() {
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));
        when(roomImageStorage.createUploadUrl(anyString(), eq("image/jpeg")))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://upload.example",
                        Map.of("Content-Type", "image/jpeg"),
                        EXPIRY
                ));

        var response = service.createUploadUrl(
                " dlx ",
                new RoomImageUploadUrlRequest("deluxe.jpeg", "IMAGE/JPEG", 1024)
        );

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(roomImageStorage).createUploadUrl(keyCaptor.capture(), eq("image/jpeg"));
        assertEquals("room-types/15/" + response.uploadId() + ".jpg", keyCaptor.getValue());
    }

    @Test
    void createUploadUrlRejectsUnsupportedTypeSizeAndLimit() {
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));

        assertThrows(
                BusinessValidationException.class,
                () -> service.createUploadUrl(
                        "DLX", new RoomImageUploadUrlRequest("deluxe.gif", "image/gif", 100)
                )
        );
        assertThrows(
                BusinessValidationException.class,
                () -> service.createUploadUrl(
                        "DLX",
                        new RoomImageUploadUrlRequest(
                                "deluxe.png",
                                "image/png",
                                10 * 1024 * 1024L + 1
                        )
                )
        );
        when(roomTypeImageRepository.countByRoomTypeId(15L)).thenReturn(20L);
        assertThrows(
                BusinessValidationException.class,
                () -> service.createUploadUrl(
                        "DLX", new RoomImageUploadUrlRequest("deluxe.webp", "image/webp", 100)
                )
        );

        verify(roomImageStorage, never()).createUploadUrl(anyString(), anyString());
    }

    @Test
    void confirmUploadPersistsMetadataAndMakesFirstImagePrimary() {
        UUID uploadId = UUID.randomUUID();
        String objectKey = "room-types/15/" + uploadId + ".png";
        when(roomTypeRepository.findForUpdateByCode("DLX")).thenReturn(Optional.of(roomType));
        when(roomImageStorage.getObjectMetadata(anyString())).thenAnswer(invocation -> {
            String candidateKey = invocation.getArgument(0);
            if (objectKey.equals(candidateKey)) {
                return new RoomImageStorage.StoredObject(1024, "image/png");
            }
            throw new StorageObjectNotFoundException("missing", null);
        });
        when(roomImageStorage.getObjectUri(objectKey)).thenReturn("minio://room-images/" + objectKey);
        when(roomTypeImageRepository.saveAndFlush(any(RoomTypeImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roomImageStorage.createDownloadUrl(objectKey))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://download.example",
                        Map.of(),
                        EXPIRY
                ));

        var response = service.confirmUpload(
                "DLX",
                new RoomImageConfirmRequest(uploadId, " Deluxe room ")
        );

        ArgumentCaptor<RoomTypeImage> imageCaptor = ArgumentCaptor.forClass(RoomTypeImage.class);
        verify(roomTypeImageRepository).saveAndFlush(imageCaptor.capture());
        assertEquals(objectKey, imageCaptor.getValue().getStorageKey());
        assertEquals("Deluxe room", imageCaptor.getValue().getAltText());
        assertTrue(imageCaptor.getValue().getIsPrimary());
        assertEquals(0, imageCaptor.getValue().getSortOrder());
        assertEquals(uploadId, response.imageId());
    }

    @Test
    void confirmUploadAppendsSecondImageWithoutChangingPrimary() {
        RoomTypeImage primaryImage = RoomTypeImage.builder()
                .roomType(roomType)
                .storageKey("room-types/15/00000000-0000-0000-0000-000000000001.jpg")
                .url("minio://room-images/primary.jpg")
                .altText("Primary")
                .isPrimary(true)
                .sortOrder(0)
                .build();
        primaryImage.setId(1L);
        roomType.getImages().add(primaryImage);

        UUID uploadId = UUID.randomUUID();
        String objectKey = "room-types/15/" + uploadId + ".webp";
        when(roomTypeRepository.findForUpdateByCode("DLX")).thenReturn(Optional.of(roomType));
        when(roomImageStorage.getObjectMetadata(anyString())).thenAnswer(invocation -> {
            String candidateKey = invocation.getArgument(0);
            if (objectKey.equals(candidateKey)) {
                return new RoomImageStorage.StoredObject(2048, "image/webp");
            }
            throw new StorageObjectNotFoundException("missing", null);
        });
        when(roomImageStorage.getObjectUri(objectKey)).thenReturn("minio://room-images/" + objectKey);
        when(roomTypeImageRepository.saveAndFlush(any(RoomTypeImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roomImageStorage.createDownloadUrl(objectKey))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://download.example/second",
                        Map.of(),
                        EXPIRY
                ));

        service.confirmUpload("DLX", new RoomImageConfirmRequest(uploadId, "Second image"));

        ArgumentCaptor<RoomTypeImage> imageCaptor = ArgumentCaptor.forClass(RoomTypeImage.class);
        verify(roomTypeImageRepository).saveAndFlush(imageCaptor.capture());
        assertFalse(imageCaptor.getValue().getIsPrimary());
        assertEquals(1, imageCaptor.getValue().getSortOrder());
        assertTrue(primaryImage.getIsPrimary());
    }

    @Test
    void confirmUploadDeletesInvalidObjectBestEffort() {
        UUID uploadId = UUID.randomUUID();
        String objectKey = "room-types/15/" + uploadId + ".webp";
        when(roomTypeRepository.findForUpdateByCode("DLX")).thenReturn(Optional.of(roomType));
        when(roomImageStorage.getObjectMetadata(anyString())).thenAnswer(invocation -> {
            String candidateKey = invocation.getArgument(0);
            if (objectKey.equals(candidateKey)) {
                return new RoomImageStorage.StoredObject(1024, "image/png");
            }
            throw new StorageObjectNotFoundException("missing", null);
        });

        assertThrows(
                BusinessValidationException.class,
                () -> service.confirmUpload(
                        "DLX", new RoomImageConfirmRequest(uploadId, "Deluxe")
                )
        );

        verify(roomImageStorage).deleteObjectBestEffort(objectKey);
        verify(roomTypeImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmUploadRejectsMissingObject() {
        UUID uploadId = UUID.randomUUID();
        when(roomTypeRepository.findForUpdateByCode("DLX")).thenReturn(Optional.of(roomType));
        when(roomImageStorage.getObjectMetadata(anyString()))
                .thenThrow(new StorageObjectNotFoundException("missing", null));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.confirmUpload(
                        "DLX", new RoomImageConfirmRequest(uploadId, "Deluxe")
                )
        );
    }
}
