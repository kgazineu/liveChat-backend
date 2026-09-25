package com.example.liveChat.infra.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryAttachmentObjectStorage implements AttachmentObjectStorage {
    private static final Duration UPLOAD_TTL = Duration.ofHours(1);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

    private final Map<String, StoredObjectMetadata> objects = new ConcurrentHashMap<>();

    @Override
    public SignedUpload signUpload(String objectKey, String contentType, long contentLength, String ownerId,
                                   String uploadId) {
        requireText(objectKey, "objectKey");
        requireText(contentType, "contentType");
        if (contentLength <= 0) throw new IllegalArgumentException("contentLength must be positive");
        requireText(ownerId, "ownerId");
        requireText(uploadId, "uploadId");

        Map<String, String> metadata = Map.of("owner-id", ownerId, "upload-id", uploadId);
        objects.put(objectKey, new StoredObjectMetadata(contentLength, contentType, metadata));
        Map<String, String> formFields = Map.of(
                "api_key", "test-api-key",
                "upload_preset", "test-private-attachments",
                "timestamp", Long.toString(Instant.now().getEpochSecond()),
                "signature", "test-signature",
                "public_id", objectKey,
                "type", "private",
                "overwrite", "false");
        Instant expiresAt = Instant.now().plus(UPLOAD_TTL);
        return new SignedUpload(opaqueUrl("attachment-upload"), "POST", formFields, expiresAt);
    }

    @Override
    public SignedDownload signDownload(String objectKey, String originalName, String contentType) {
        requireText(originalName, "originalName");
        requireText(contentType, "contentType");
        requireObject(objectKey);
        return new SignedDownload(opaqueUrl("attachment-download"), Instant.now().plus(DOWNLOAD_TTL));
    }

    @Override
    public StoredObjectMetadata inspect(String objectKey, String contentType) {
        requireText(contentType, "contentType");
        return requireObject(objectKey);
    }

    @Override
    public void delete(String objectKey, String contentType) {
        requireText(contentType, "contentType");
        requireText(objectKey, "objectKey");
        objects.remove(objectKey);
    }

    private StoredObjectMetadata requireObject(String objectKey) {
        requireText(objectKey, "objectKey");
        StoredObjectMetadata metadata = objects.get(objectKey);
        if (metadata == null) {
            throw new AttachmentObjectNotFoundException();
        }
        return metadata;
    }

    private URI opaqueUrl(String host) {
        return URI.create("memory://" + host + "/" + UUID.randomUUID());
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
