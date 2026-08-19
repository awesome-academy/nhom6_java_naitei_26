package com.example.hotelmanagement.services;

import java.time.OffsetDateTime;
import java.util.Map;

public interface RoomImageStorage {

    PresignedUrl createUploadUrl(String objectKey, String contentType);

    PresignedUrl createDownloadUrl(String objectKey);

    StoredObject getObjectMetadata(String objectKey);

    String getObjectUri(String objectKey);

    void deleteObjectBestEffort(String objectKey);

    record PresignedUrl(
            String url,
            Map<String, String> requiredHeaders,
            OffsetDateTime expiresAt
    ) {
    }

    record StoredObject(long size, String contentType) {
    }
}
