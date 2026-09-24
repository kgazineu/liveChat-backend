package com.example.liveChat.infra.ratelimit;

import com.example.liveChat.exceptions.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTests {
    @Mock private StringRedisTemplate redisTemplate;

    @Test
    void scriptPerformsIncrementAndExpirationInOneAtomicExecution() {
        assertThat(RedisRateLimiter.LUA_SCRIPT)
                .contains("redis.call('INCR', KEYS[1])")
                .contains("redis.call('PTTL', KEYS[1])")
                .contains("redis.call('PEXPIRE', KEYS[1], ARGV[1])")
                .contains("return {count, ttl}");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void executesTheScriptWithANamespacedHashedKey() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of(1L, 10_000L));
        RedisRateLimiter rateLimiter = new RedisRateLimiter(redisTemplate);

        assertThatCode(() -> rateLimiter.check("password-reset", "person@example.com", 3, Duration.ofSeconds(10)))
                .doesNotThrowAnyException();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), eq("10000"));
        assertThat(keysCaptor.getValue()).singleElement().satisfies(key -> {
            assertThat(key).startsWith("livechat:ratelimit:password-reset:");
            assertThat(key).doesNotContain("person@example.com");
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsWhenTheAtomicCounterExceedsTheLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of(6L, 1_001L));
        RedisRateLimiter rateLimiter = new RedisRateLimiter(redisTemplate);

        assertThatThrownBy(() -> rateLimiter.check("login", "client", 5, Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(2));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void failsClosedWhenRedisIsUnavailable() {
        RedisConnectionFailureException redisFailure = new RedisConnectionFailureException("connection details");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenThrow(redisFailure);
        RedisRateLimiter rateLimiter = new RedisRateLimiter(redisTemplate);

        assertThatThrownBy(() -> rateLimiter.check("login", "client", 5, Duration.ofSeconds(10)))
                .isInstanceOf(RateLimitInfrastructureException.class)
                .hasMessage("Rate limiting service is temporarily unavailable")
                .hasCause(redisFailure);
    }
}
