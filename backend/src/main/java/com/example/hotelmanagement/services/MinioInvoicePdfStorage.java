package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class MinioInvoicePdfStorage implements InvoicePdfStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioInvoicePdfStorage.class);
    private static final String CONTENT_TYPE = "application/pdf";

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final Clock clock;
    private volatile boolean isBucketReady;

    public MinioInvoicePdfStorage(MinioClient minioClient, MinioProperties properties, Clock clock) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void uploadPdf(String objectKey, byte[] pdfBytes) {
        ensureBucketExists();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.invoicesBucket())
                            .object(objectKey)
                            .stream(inputStream, pdfBytes.length, -1)
                            .contentType(CONTENT_TYPE)
                            .build()
            );
        } catch (Exception exception) {
            log.error("Failed to upload invoice PDF objectKey={}", objectKey, exception);
            throw new StorageUnavailableException("Could not upload invoice PDF", exception);
        }
    }

    @Override
    public PresignedUrl createDownloadUrl(String objectKey) {
        ensureBucketExists();
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.invoicesBucket())
                            .object(objectKey)
                            .expiry(toExpirySeconds(properties.invoicePdfUrlTtl()))
                            .build()
            );
            return new PresignedUrl(url, OffsetDateTime.now(clock).plus(properties.invoicePdfUrlTtl()));
        } catch (Exception exception) {
            log.error("Failed to create invoice PDF download URL objectKey={}", objectKey, exception);
            throw new StorageUnavailableException("Could not create invoice PDF download URL", exception);
        }
    }

    @Override
    public String getObjectUri(String objectKey) {
        return "minio://" + properties.invoicesBucket() + "/" + objectKey;
    }

    private synchronized void ensureBucketExists() {
        if (isBucketReady) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.invoicesBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.invoicesBucket()).build()
                );
            }
            isBucketReady = true;
        } catch (Exception exception) {
            log.error("Failed to initialize MinIO bucket bucket={}", properties.invoicesBucket(), exception);
            throw new StorageUnavailableException("Could not initialize invoice PDF storage", exception);
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
