package com.example.liveChat.infra.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SignedUpload(URI url, Map<String, String> requiredHeaders, Instant expiresAt) {
    public SignedUpload {
        Objects.requireNonNull(url, "url is required");
        requiredHeaders = Map.copyOf(Objects.requireNonNull(requiredHeaders, "requiredHeaders is required"));
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
