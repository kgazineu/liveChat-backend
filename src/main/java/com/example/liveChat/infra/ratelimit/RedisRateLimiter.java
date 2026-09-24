package com.example.liveChat.infra.ratelimit;

import com.example.liveChat.exceptions.RateLimitExceededException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Profile("!test")
public class RedisRateLimiter implements RateLimiter {
    static final String LUA_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """;

    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, List.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void check(String scope, String key, int limit, Duration window) {
        long windowMillis = RateLimitSupport.validateAndGetWindowMillis(scope, key, limit, window);
        List<?> result;
        try {
            result = redisTemplate.execute(
                    SCRIPT,
                    List.of(RateLimitSupport.storageKey(scope, key)),
                    Long.toString(windowMillis));
        } catch (RuntimeException exception) {
            throw new RateLimitInfrastructureException(exception);
        }

        if (result == null || result.size() != 2
                || !(result.get(0) instanceof Number count)
                || !(result.get(1) instanceof Number ttlMillis)) {
            throw new RateLimitInfrastructureException();
        }

        if (count.longValue() > limit) {
            throw new RateLimitExceededException(RateLimitSupport.retryAfterSeconds(ttlMillis.longValue()));
        }
    }
}
