package com.example.liveChat.infra.storage;

import java.util.Map;
import java.util.Objects;

public record StoredObjectMetadata(long contentLength, String contentType, Map<String, String> metadata) {
    public StoredObjectMetadata {
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        Objects.requireNonNull(contentType, "contentType is required");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata is required"));
    }
}
