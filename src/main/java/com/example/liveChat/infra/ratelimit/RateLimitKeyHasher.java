package com.example.liveChat.infra.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class RateLimitKeyHasher {
    private RateLimitKeyHasher() {
    }

    public static String forAccount(String accountIdentifier) {
        return "account:" + sha256(requireIdentifier(accountIdentifier, "accountIdentifier"));
    }

    public static String forEmail(String email) {
        String normalizedEmail = requireIdentifier(email, "email").trim().toLowerCase(Locale.ROOT);
        return "email:" + sha256(normalizedEmail);
    }

    public static String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
