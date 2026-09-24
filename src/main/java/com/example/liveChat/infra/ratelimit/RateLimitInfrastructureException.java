package com.example.liveChat.infra.ratelimit;

public class RateLimitInfrastructureException extends RuntimeException {
    private static final String SAFE_MESSAGE = "Rate limiting service is temporarily unavailable";

    public RateLimitInfrastructureException(Throwable cause) {
        super(SAFE_MESSAGE, cause);
    }

    public RateLimitInfrastructureException() {
        super(SAFE_MESSAGE);
    }
}
