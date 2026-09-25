package com.example.liveChat.infra.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class S3AttachmentObjectStorageTests {
    @Test
    void signsUploadAgainstThePublicEndpointWithRequiredHeadersAndPrivateObjectKey() {
        S3AttachmentStorageProperties properties = properties();
        Instant before = Instant.now();

        try (CloseableStorage storage = new CloseableStorage(new S3AttachmentObjectStorage(properties))) {
            SignedUpload signed = storage.delegate.presignPut(
                    "message-attachments/user-id/object-id", "image/png", 2048, "user-id", "upload-id");

            assertThat(signed.url().getScheme()).isEqualTo("https");
            assertThat(signed.url().getHost()).isEqualTo("objects.example.test");
            assertThat(signed.url().getPath()).isEqualTo("/attachments/message-attachments/user-id/object-id");
            assertThat(signed.url().getQuery()).contains("X-Amz-Signature");
            assertThat(signed.requiredHeaders())
                    .containsEntry("content-type", "image/png")
                    .containsEntry("x-amz-meta-owner-id", "user-id")
                    .containsEntry("x-amz-meta-upload-id", "upload-id")
                    .doesNotContainKey("host");
            assertThat(signed.expiresAt()).isAfter(before.plus(Duration.ofMinutes(4)));
        }
    }

    @Test
    void signsDownloadWithoutExposingTheInternalEndpoint() {
        try (CloseableStorage storage = new CloseableStorage(new S3AttachmentObjectStorage(properties()))) {
            SignedDownload signed = storage.delegate.presignGet("message-attachments/user-id/object-id");

            assertThat(signed.url().getHost()).isEqualTo("objects.example.test");
            assertThat(signed.url().toString()).doesNotContain("s3-internal.example.test");
        }
    }

    private S3AttachmentStorageProperties properties() {
        S3AttachmentStorageProperties properties = new S3AttachmentStorageProperties();
        properties.setInternalEndpoint("http://s3-internal.example.test:9000");
        properties.setPublicEndpoint("https://objects.example.test");
        properties.setRegion("us-east-1");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setBucket("attachments");
        properties.setPathStyle(true);
        properties.setUploadUrlTtl(Duration.ofMinutes(5));
        properties.setDownloadUrlTtl(Duration.ofMinutes(5));
        return properties;
    }

    private static final class CloseableStorage implements AutoCloseable {
        private final S3AttachmentObjectStorage delegate;

        private CloseableStorage(S3AttachmentObjectStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
