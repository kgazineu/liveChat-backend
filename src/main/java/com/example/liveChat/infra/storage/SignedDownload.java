package com.example.liveChat.infra.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record SignedDownload(URI url, Instant expiresAt) {
    public SignedDownload {
        Objects.requireNonNull(url, "url is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
