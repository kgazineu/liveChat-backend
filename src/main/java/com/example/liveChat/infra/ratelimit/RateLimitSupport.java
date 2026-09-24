package com.example.liveChat.infra.ratelimit;

import java.time.Duration;
import java.util.regex.Pattern;

final class RateLimitSupport {
    static final String KEY_NAMESPACE = "livechat:ratelimit:";
    private static final Pattern SCOPE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RateLimitSupport() {
    }

    static long validateAndGetWindowMillis(String scope, String key, int limit, Duration window) {
        if (scope == null || !SCOPE_PATTERN.matcher(scope).matches()) {
            throw new IllegalArgumentException("scope must contain only letters, numbers, '.', '_' or '-' and have at most 64 characters");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be greater than zero");
        }

        try {
            long windowMillis = window.toMillis();
            if (windowMillis <= 0) {
                throw new IllegalArgumentException("window must be at least one millisecond");
            }
            return windowMillis;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("window is too large", exception);
        }
    }

    static String storageKey(String scope, String key) {
        return KEY_NAMESPACE + scope + ":" + RateLimitKeyHasher.sha256(key);
    }

    static long retryAfterSeconds(long remainingMillis) {
        long positiveMillis = Math.max(remainingMillis, 1);
        long wholeSeconds = positiveMillis / 1_000;
        return wholeSeconds + (positiveMillis % 1_000 == 0 ? 0 : 1);
    }
}
