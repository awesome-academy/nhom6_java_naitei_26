package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import com.example.hotelmanagement.exceptions.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Creates application-owned buckets when the backend is started outside Docker Compose. */
@Component
@ConditionalOnProperty(
        prefix = "app.minio",
        name = "initialize-on-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class MinioBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final long RETRY_DELAY_MILLIS = 2_000L;

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioBucketInitializer(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeBuckets();
    }

    void initializeBuckets() {
        List<String> buckets = List.of(
                properties.roomTypeImagesBucket(),
                properties.avatarsBucket(),
                properties.invoicesBucket()
        );
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                buckets.forEach(this::ensureBucketExists);
                log.info("MinIO buckets are ready buckets={}", buckets);
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("MinIO is not ready yet attempt={}/{}", attempt, MAX_ATTEMPTS, exception);
                    sleepBeforeRetry();
                }
            }
        }
        log.error("Failed to initialize MinIO buckets after {} attempts", MAX_ATTEMPTS, lastFailure);
        throw new StorageUnavailableException("Could not initialize MinIO buckets", lastFailure);
    }

    private void ensureBucketExists(String bucket) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket={}", bucket);
            }
        } catch (ErrorResponseException exception) {
            String errorCode = exception.errorResponse().code();
            if ("BucketAlreadyOwnedByYou".equals(errorCode)
                    || "BucketAlreadyExists".equals(errorCode)) {
                log.debug("MinIO bucket was created concurrently bucket={}", bucket);
                return;
            }
            throw new StorageUnavailableException("Could not initialize MinIO bucket " + bucket, exception);
        } catch (Exception exception) {
            throw new StorageUnavailableException("Could not initialize MinIO bucket " + bucket, exception);
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StorageUnavailableException("Interrupted while waiting for MinIO", exception);
        }
    }
}
