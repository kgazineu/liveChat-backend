package com.example.liveChat.infra.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    void check(String scope, String key, int limit, Duration window);
}
