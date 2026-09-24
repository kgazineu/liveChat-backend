package com.example.liveChat.infra.ratelimit;

import com.example.liveChat.exceptions.RateLimitExceededException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile("test")
public class InMemoryRateLimiter implements RateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void check(String scope, String key, int limit, Duration window) {
        long windowMillis = RateLimitSupport.validateAndGetWindowMillis(scope, key, limit, window);
        long now = clock.millis();
        AtomicReference<Decision> decision = new AtomicReference<>();

        windows.compute(RateLimitSupport.storageKey(scope, key), (ignored, current) -> {
            Window updated;
            if (current == null || now >= current.expiresAtMillis()) {
                updated = new Window(1, addWithoutOverflow(now, windowMillis));
            } else {
                updated = new Window(current.count() == Long.MAX_VALUE ? Long.MAX_VALUE : current.count() + 1,
                        current.expiresAtMillis());
            }
            decision.set(new Decision(updated.count(), Math.max(1, updated.expiresAtMillis() - now)));
            return updated;
        });

        Decision result = decision.get();
        if (result.count() > limit) {
            throw new RateLimitExceededException(RateLimitSupport.retryAfterSeconds(result.remainingMillis()));
        }
    }

    private static long addWithoutOverflow(long value, long increment) {
        try {
            return Math.addExact(value, increment);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private record Window(long count, long expiresAtMillis) {
    }

    private record Decision(long count, long remainingMillis) {
    }
}
