package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import io.minio.MinioClient;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service("roomImageStorage")
public class MinioRoomImageStorage implements RoomImageStorage {

    private final MinioBucketImageStorage delegate;

    public MinioRoomImageStorage(MinioClient minioClient, MinioProperties properties, Clock clock) {
        this.delegate = new MinioBucketImageStorage(
                minioClient,
                properties.roomImagesBucket(),
                properties.uploadUrlTtl(),
                properties.downloadUrlTtl(),
                clock,
                "room image"
        );
    }

    @Override
    public PresignedUrl createUploadUrl(String objectKey, String contentType) {
        return delegate.createUploadUrl(objectKey, contentType);
    }

    @Override
    public PresignedUrl createDownloadUrl(String objectKey) {
        return delegate.createDownloadUrl(objectKey);
    }

    @Override
    public StoredObject getObjectMetadata(String objectKey) {
        return delegate.getObjectMetadata(objectKey);
    }

    @Override
    public String getObjectUri(String objectKey) {
        return delegate.getObjectUri(objectKey);
    }

    @Override
    public void deleteObjectBestEffort(String objectKey) {
        delegate.deleteObjectBestEffort(objectKey);
    }
}
