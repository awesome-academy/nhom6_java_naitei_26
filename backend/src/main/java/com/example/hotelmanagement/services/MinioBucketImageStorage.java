package com.example.hotelmanagement.services;

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

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/** Shared MinIO implementation for image-like objects stored in one bucket. */
final class MinioBucketImageStorage implements RoomImageStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketImageStorage.class);

    private final MinioClient minioClient;
    private final String bucket;
    private final Duration uploadUrlTtl;
    private final Duration downloadUrlTtl;
    private final Clock clock;
    private final String resourceLabel;
    private volatile boolean isBucketReady;

    MinioBucketImageStorage(
            MinioClient minioClient,
            String bucket,
            Duration uploadUrlTtl,
            Duration downloadUrlTtl,
            Clock clock,
            String resourceLabel
    ) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.uploadUrlTtl = uploadUrlTtl;
        this.downloadUrlTtl = downloadUrlTtl;
        this.clock = clock;
        this.resourceLabel = resourceLabel;
    }

    @Override
    public PresignedUrl createUploadUrl(String objectKey, String contentType) {
        ensureBucketExists();
        Map<String, String> headers = Map.of("Content-Type", contentType);
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(toExpirySeconds(uploadUrlTtl))
                            .extraHeaders(headers)
                            .build()
            );
            return new PresignedUrl(url, headers, OffsetDateTime.now(clock).plus(uploadUrlTtl));
        } catch (Exception exception) {
            log.error("Failed to create {} upload URL bucket={} objectKey={}",
                    resourceLabel, bucket, objectKey, exception);
            throw new StorageUnavailableException("Could not create image upload URL", exception);
        }
    }

    @Override
    public PresignedUrl createDownloadUrl(String objectKey) {
        ensureBucketExists();
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(toExpirySeconds(downloadUrlTtl))
                            .build()
            );
            return new PresignedUrl(url, Map.of(), OffsetDateTime.now(clock).plus(downloadUrlTtl));
        } catch (Exception exception) {
            log.error("Failed to create {} download URL bucket={} objectKey={}",
                    resourceLabel, bucket, objectKey, exception);
            throw new StorageUnavailableException("Could not create image download URL", exception);
        }
    }

    @Override
    public StoredObject getObjectMetadata(String objectKey) {
        ensureBucketExists();
        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
            return new StoredObject(response.size(), response.contentType());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                log.debug("{} object was not found bucket={} objectKey={}", resourceLabel, bucket, objectKey);
                throw new StorageObjectNotFoundException("Uploaded image object was not found", exception);
            }
            log.error("Failed to read {} metadata bucket={} objectKey={}",
                    resourceLabel, bucket, objectKey, exception);
            throw new StorageUnavailableException("Could not verify uploaded image", exception);
        } catch (Exception exception) {
            log.error("Failed to read {} metadata bucket={} objectKey={}",
                    resourceLabel, bucket, objectKey, exception);
            throw new StorageUnavailableException("Could not verify uploaded image", exception);
        }
    }

    @Override
    public String getObjectUri(String objectKey) {
        return "minio://" + bucket + "/" + objectKey;
    }

    @Override
    public void deleteObjectBestEffort(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
        } catch (Exception exception) {
            log.warn("Failed to clean up {} object bucket={} objectKey={}",
                    resourceLabel, bucket, objectKey, exception);
        }
    }

    private synchronized void ensureBucketExists() {
        if (isBucketReady) {
            return;
        }
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            isBucketReady = true;
        } catch (Exception exception) {
            log.error("Failed to initialize {} bucket={}", resourceLabel, bucket, exception);
            throw new StorageUnavailableException("Could not initialize image storage", exception);
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
