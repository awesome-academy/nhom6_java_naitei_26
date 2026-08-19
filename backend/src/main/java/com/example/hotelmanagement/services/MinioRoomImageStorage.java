package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.exceptions.StorageObjectNotFoundException;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class MinioRoomImageStorage implements RoomImageStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioRoomImageStorage.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final Clock clock;
    private volatile boolean isBucketReady;

    public MinioRoomImageStorage(MinioClient minioClient, MinioProperties properties, Clock clock) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PresignedUrl createUploadUrl(String objectKey, String contentType) {
        ensureBucketExists();
        Map<String, String> headers = Map.of("Content-Type", contentType);
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(properties.roomImagesBucket())
                            .object(objectKey)
                            .expiry(toExpirySeconds(properties.uploadUrlTtl()))
                            .extraHeaders(headers)
                            .build()
            );
            return new PresignedUrl(
                    url,
                    headers,
                    OffsetDateTime.now(clock).plus(properties.uploadUrlTtl())
            );
        } catch (Exception exception) {
            log.error("Failed to create room image upload URL objectKey={}", objectKey, exception);
            throw new StorageUnavailableException("Could not create room image upload URL", exception);
        }
    }

    @Override
    public PresignedUrl createDownloadUrl(String objectKey) {
        ensureBucketExists();
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.roomImagesBucket())
                            .object(objectKey)
                            .expiry(toExpirySeconds(properties.downloadUrlTtl()))
                            .build()
            );
            return new PresignedUrl(
                    url,
                    Map.of(),
                    OffsetDateTime.now(clock).plus(properties.downloadUrlTtl())
            );
        } catch (Exception exception) {
            log.error("Failed to create room image download URL objectKey={}", objectKey, exception);
            throw new StorageUnavailableException("Could not create room image download URL", exception);
        }
    }

    @Override
    public StoredObject getObjectMetadata(String objectKey) {
        ensureBucketExists();
        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.roomImagesBucket())
                            .object(objectKey)
                            .build()
            );
            return new StoredObject(response.size(), response.contentType());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                log.debug("Room image object was not found objectKey={}", objectKey);
                throw new StorageObjectNotFoundException("Uploaded room image object was not found", exception);
            }
            log.error("Failed to read room image metadata objectKey={}", objectKey, exception);
            throw new StorageUnavailableException("Could not verify uploaded room image", exception);
        } catch (Exception exception) {
            log.error("Failed to read room image metadata objectKey={}", objectKey, exception);
            throw new StorageUnavailableException("Could not verify uploaded room image", exception);
        }
    }

    @Override
    public String getObjectUri(String objectKey) {
        return "minio://" + properties.roomImagesBucket() + "/" + objectKey;
    }

    @Override
    public void deleteObjectBestEffort(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.roomImagesBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            log.warn("Failed to clean up invalid room image objectKey={}", objectKey, exception);
        }
    }

    private synchronized void ensureBucketExists() {
        if (isBucketReady) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.roomImagesBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.roomImagesBucket()).build()
                );
            }
            isBucketReady = true;
        } catch (Exception exception) {
            log.error("Failed to initialize MinIO bucket bucket={}", properties.roomImagesBucket(), exception);
            throw new StorageUnavailableException("Could not initialize room image storage", exception);
        }
    }

    private int toExpirySeconds(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds < 1 || seconds > 604_800) {
            throw new StorageUnavailableException("Presigned URL TTL must be between 1 second and 7 days");
        }
        return Math.toIntExact(seconds);
    }
}
