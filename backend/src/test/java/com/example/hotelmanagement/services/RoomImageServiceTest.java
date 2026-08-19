package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.dto.roomimage.RoomImageConfirmRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageOrderRequest;
import com.example.hotelmanagement.dto.roomimage.RoomImageUploadUrlRequest;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomImage;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.StorageObjectNotFoundException;
import com.example.hotelmanagement.repositories.RoomImageRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class RoomImageServiceTest {

    private static final OffsetDateTime EXPIRY = OffsetDateTime.of(
            2026, 8, 19, 10, 0, 0, 0, ZoneOffset.UTC
    );

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomImageRepository roomImageRepository;
    @Mock
    private RoomImageStorage roomImageStorage;

    private RoomImageService service;
    private Room room;

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
                20
        );
        service = new RoomImageService(
                roomRepository,
                roomImageRepository,
                roomImageStorage,
                properties
        );
        room = Room.builder().roomNumber("A101").build();
        room.setId(10L);
    }

    @Test
    void createUploadUrlUsesServerGeneratedIsolatedObjectKey() {
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));
        when(roomImageStorage.createUploadUrl(anyString(), eq("image/jpeg")))
                .thenReturn(new RoomImageStorage.PresignedUrl(
                        "https://upload.example", Map.of("Content-Type", "image/jpeg"), EXPIRY
                ));

        var response = service.createUploadUrl(
                " a101 ", new RoomImageUploadUrlRequest("room.jpeg", "IMAGE/JPEG", 1024)
        );

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(roomImageStorage).createUploadUrl(keyCaptor.capture(), eq("image/jpeg"));
        assertEquals("rooms/10/" + response.uploadId() + ".jpg", keyCaptor.getValue());
        assertEquals("https://upload.example", response.uploadUrl());
    }

    @Test
    void createUploadUrlRejectsUnsupportedTypeAndOversizedImage() {
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));

        assertThrows(
                BusinessValidationException.class,
                () -> service.createUploadUrl(
                        "A101", new RoomImageUploadUrlRequest("room.gif", "image/gif", 100)
                )
        );
        assertThrows(
                BusinessValidationException.class,
                () -> service.createUploadUrl(
                        "A101", new RoomImageUploadUrlRequest("room.png", "image/png", 10 * 1024 * 1024L + 1)
                )
        );

        verify(roomImageStorage, never()).createUploadUrl(anyString(), anyString());
    }

    @Test
    void createUploadUrlRejectsRoomAtImageLimit() {
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));
        when(roomImageRepository.countByRoomId(10L)).thenReturn(20L);

        assertThrows(
                BusinessValidationException.class,
                () -> service.createUploadUrl(
                        "A101", new RoomImageUploadUrlRequest("room.webp", "image/webp", 100)
                )
        );
    }

    @Test
    void confirmUploadPersistsMetadataAndMakesFirstImagePrimary() {
        UUID uploadId = UUID.randomUUID();
        String objectKey = "rooms/10/" + uploadId + ".png";
        when(roomRepository.findForUpdateByRoomNumber("A101"))
                .thenReturn(Optional.of(room));
        when(roomImageStorage.getObjectMetadata(anyString())).thenAnswer(invocation -> {
            String candidateKey = invocation.getArgument(0);
            if (objectKey.equals(candidateKey)) {
                return new RoomImageStorage.StoredObject(1024, "image/png");
            }
            throw new StorageObjectNotFoundException("missing", null);
        });
        when(roomImageStorage.getObjectUri(objectKey)).thenReturn("minio://room-images/" + objectKey);
        when(roomImageRepository.saveAndFlush(any(RoomImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roomImageStorage.createDownloadUrl(objectKey))
                .thenReturn(new RoomImageStorage.PresignedUrl("https://download.example", Map.of(), EXPIRY));

        var response = service.confirmUpload(
                "A101", new RoomImageConfirmRequest(uploadId, " Pool view ")
        );

        ArgumentCaptor<RoomImage> captor = ArgumentCaptor.forClass(RoomImage.class);
        verify(roomImageRepository).saveAndFlush(captor.capture());
        assertEquals(objectKey, captor.getValue().getStorageKey());
        assertEquals("Pool view", captor.getValue().getAltText());
        assertTrue(captor.getValue().getIsPrimary());
        assertEquals(0, captor.getValue().getSortOrder());
        assertEquals(uploadId, response.imageId());
    }

    @Test
    void confirmUploadMapsMissingObjectToNotFound() {
        UUID uploadId = UUID.randomUUID();
        String objectKey = "rooms/10/" + uploadId + ".jpg";
        when(roomRepository.findForUpdateByRoomNumber("A101"))
                .thenReturn(Optional.of(room));
        when(roomImageStorage.getObjectMetadata(anyString()))
                .thenThrow(new StorageObjectNotFoundException(objectKey, null));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.confirmUpload(
                        "A101", new RoomImageConfirmRequest(uploadId, "Room")
                )
        );
    }

    @Test
    void confirmUploadDeletesInvalidObjectBestEffort() {
        UUID uploadId = UUID.randomUUID();
        String objectKey = "rooms/10/" + uploadId + ".webp";
        when(roomRepository.findForUpdateByRoomNumber("A101"))
                .thenReturn(Optional.of(room));
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
                        "A101", new RoomImageConfirmRequest(uploadId, "Room")
                )
        );

        verify(roomImageStorage).deleteObjectBestEffort(objectKey);
        verify(roomImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void reorderRequiresEveryImageAndSetsSinglePrimary() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        RoomImage first = image(firstId, 0, true);
        RoomImage second = image(secondId, 1, false);
        room.getImages().add(first);
        room.getImages().add(second);
        when(roomRepository.findForUpdateByRoomNumber("A101"))
                .thenReturn(Optional.of(room));
        when(roomImageRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roomImageStorage.createDownloadUrl(anyString()))
                .thenReturn(new RoomImageStorage.PresignedUrl("https://download.example", Map.of(), EXPIRY));

        var responses = service.reorderImages(
                "A101", new RoomImageOrderRequest(List.of(secondId, firstId))
        );

        assertEquals(secondId, responses.get(0).imageId());
        assertTrue(second.getIsPrimary());
        assertFalse(first.getIsPrimary());
        assertEquals(0, second.getSortOrder());
        assertEquals(1, first.getSortOrder());
    }

    @Test
    void reorderRejectsDuplicateOrIncompleteIds() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        room.getImages().add(image(firstId, 0, true));
        room.getImages().add(image(secondId, 1, false));
        when(roomRepository.findForUpdateByRoomNumber("A101"))
                .thenReturn(Optional.of(room));

        assertThrows(
                BusinessValidationException.class,
                () -> service.reorderImages("A101", new RoomImageOrderRequest(List.of(firstId, firstId)))
        );
        assertThrows(
                BusinessValidationException.class,
                () -> service.reorderImages("A101", new RoomImageOrderRequest(List.of(firstId)))
        );

        verify(roomImageRepository, never()).saveAllAndFlush(any());
    }

    private RoomImage image(UUID id, int sortOrder, boolean primary) {
        RoomImage image = RoomImage.builder()
                .room(room)
                .url("minio://room-images/rooms/10/" + id + ".jpg")
                .storageKey("rooms/10/" + id + ".jpg")
                .altText("Room")
                .isPrimary(primary)
                .sortOrder(sortOrder)
                .build();
        image.setId((long) sortOrder + 1);
        return image;
    }
}
