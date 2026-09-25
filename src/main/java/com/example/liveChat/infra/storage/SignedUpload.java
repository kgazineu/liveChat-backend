package com.example.liveChat.infra.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SignedUpload(URI url, String method, Map<String, String> formFields, Instant expiresAt) {
    public SignedUpload {
        Objects.requireNonNull(url, "url is required");
        Objects.requireNonNull(method, "method is required");
        formFields = Map.copyOf(Objects.requireNonNull(formFields, "formFields is required"));
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
