package com.example.liveChat.infra.ratelimit;

import com.example.liveChat.exceptions.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRateLimiterTests {
    @Test
    void rejectsRequestsAboveTheLimitWithTheRemainingWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

        rateLimiter.check("login", "client", 2, Duration.ofSeconds(10));
        rateLimiter.check("login", "client", 2, Duration.ofSeconds(10));
        clock.advance(Duration.ofMillis(1_500));

        assertThatThrownBy(() -> rateLimiter.check("login", "client", 2, Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(9));
    }

    @Test
    void startsAnewFixedWindowAfterExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

        rateLimiter.check("login", "client", 1, Duration.ofSeconds(5));
        clock.advance(Duration.ofSeconds(5));

        assertThatCode(() -> rateLimiter.check("login", "client", 1, Duration.ofSeconds(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void makesConcurrentDecisionsAtomically() throws Exception {
        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(
                new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));
        int attempts = 100;
        int limit = 17;
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        rateLimiter.check("concurrent", "same-client", limit, Duration.ofMinutes(1));
                        allowed.incrementAndGet();
                    } catch (RateLimitExceededException exception) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(allowed).hasValue(limit);
        assertThat(rejected).hasValue(attempts - limit);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
