package com.example.liveChat.infra.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudinaryAttachmentObjectStorageTests {
    @Test
    void signsPrivateMultipartUploadWithoutExposingTheApiSecret() {
        CloudinaryAttachmentObjectStorage storage = new CloudinaryAttachmentObjectStorage(properties());
        Instant before = Instant.now();

        SignedUpload signed = storage.signUpload(
                "message-attachments/user-id/object-id", "image/png", 2048, "user-id", "upload-id");

        assertThat(signed.url().getScheme()).isEqualTo("https");
        assertThat(signed.url().getHost()).isEqualTo("api.cloudinary.com");
        assertThat(signed.url().getPath()).isEqualTo("/v1_1/test-cloud/image/upload");
        assertThat(signed.method()).isEqualTo("POST");
        assertThat(signed.formFields())
                .containsEntry("api_key", "test-api-key")
                .containsEntry("upload_preset", "livechat-private-attachments")
                .containsEntry("public_id", "message-attachments/user-id/object-id")
                .containsEntry("type", "private")
                .containsEntry("overwrite", "false")
                .containsEntry("allowed_formats", "png")
                .containsEntry("headers", "X-Robots-Tag: noindex");
        assertThat(signed.formFields().get("context"))
                .isEqualTo("owner_id=user-id|upload_id=upload-id|content_type=image/png|declared_size=2048");
        assertThat(signed.formFields().get("signature")).isNotBlank();
        assertThat(signed.formFields()).doesNotContainValue("test-api-secret");
        assertThat(signed.expiresAt()).isAfter(before.plus(Duration.ofMinutes(59)));
    }

    @Test
    void usesTheExpectedResourceTypeAndSignsTemporaryPrivateDownloads() {
        CloudinaryAttachmentObjectStorage storage = new CloudinaryAttachmentObjectStorage(properties());
        Instant before = Instant.now();

        SignedUpload rawUpload = storage.signUpload(
                "message-attachments/user-id/report.pdf", "application/pdf", 4096, "user-id", "upload-id");
        SignedDownload download = storage.signDownload(
                "message-attachments/user-id/report.pdf", "report.pdf", "application/pdf");

        assertThat(rawUpload.url().getPath()).isEqualTo("/v1_1/test-cloud/raw/upload");
        assertThat(rawUpload.formFields()).containsEntry("allowed_formats", "pdf");
        assertThat(download.url().getScheme()).isEqualTo("https");
        assertThat(download.url().getHost()).isEqualTo("api.cloudinary.com");
        assertThat(download.url().getPath()).isEqualTo("/v1_1/test-cloud/raw/download");
        assertThat(download.url().getQuery())
                .contains("public_id=message-attachments/user-id/report.pdf")
                .contains("format=pdf")
                .contains("type=private")
                .contains("signature=")
                .doesNotContain("test-api-secret");
        assertThat(download.expiresAt()).isAfter(before.plus(Duration.ofMinutes(4)));
    }

    @Test
    void inspectsPrivateAssetMetadataThroughTheUploadApi() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.explicit(eq("message-attachments/user-id/object-id"), anyMap())).thenReturn(Map.of(
                "public_id", "message-attachments/user-id/object-id",
                "resource_type", "image",
                "type", "private",
                "format", "png",
                "bytes", 2048,
                "width", 640,
                "height", 480,
                "context", Map.of("custom", Map.of(
                        "owner_id", "user-id",
                        "upload_id", "upload-id"))));
        CloudinaryAttachmentObjectStorage storage =
                new CloudinaryAttachmentObjectStorage(properties(), cloudinary);

        StoredObjectMetadata metadata = storage.inspect(
                "message-attachments/user-id/object-id", "image/png");

        assertThat(metadata.contentLength()).isEqualTo(2048);
        assertThat(metadata.contentType()).isEqualTo("image/png");
        assertThat(metadata.metadata())
                .containsEntry("owner-id", "user-id")
                .containsEntry("upload-id", "upload-id");
    }

    @Test
    void mapsACloudinaryNotFoundResponseToAnIncompleteUpload() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.explicit(eq("missing"), anyMap())).thenReturn(Map.of(
                "error", Map.of("http_code", 404, "message", "Resource not found")));
        CloudinaryAttachmentObjectStorage storage =
                new CloudinaryAttachmentObjectStorage(properties(), cloudinary);

        assertThatThrownBy(() -> storage.inspect("missing", "image/png"))
                .isInstanceOf(AttachmentObjectNotFoundException.class)
                .hasMessage("Upload has not been completed or is no longer available");
    }

    private CloudinaryAttachmentStorageProperties properties() {
        CloudinaryAttachmentStorageProperties properties = new CloudinaryAttachmentStorageProperties();
        properties.setCloudName("test-cloud");
        properties.setApiKey("test-api-key");
        properties.setApiSecret("test-api-secret");
        properties.setUploadPreset("livechat-private-attachments");
        properties.setDownloadUrlTtl(Duration.ofMinutes(5));
        return properties;
    }
}
