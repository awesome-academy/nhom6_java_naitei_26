package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioBucketInitializerTest {

    @Mock
    private MinioClient minioClient;

    private MinioBucketInitializer initializer;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties(
                "http://localhost:9000",
                "access",
                "secret",
                "room-images-legacy",
                "room-type-images",
                "avatars",
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                10 * 1024 * 1024,
                20,
                "invoices",
                Duration.ofHours(1)
        );
        initializer = new MinioBucketInitializer(minioClient, properties);
    }

    @Test
    void createsOnlyApplicationBucketsWhenMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        initializer.initializeBuckets();

        ArgumentCaptor<MakeBucketArgs> captor = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient, org.mockito.Mockito.times(3)).makeBucket(captor.capture());
        assertThat(captor.getAllValues().stream().map(MakeBucketArgs::bucket))
                .containsExactlyInAnyOrder("room-type-images", "avatars", "invoices");
    }

    @Test
    void doesNotCreateBucketsThatAlreadyExist() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        initializer.initializeBuckets();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }
}
