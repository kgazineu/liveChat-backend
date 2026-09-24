package com.example.liveChat.infra.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyHasherTests {
    @Test
    void hashesAccountIdentifiersWithoutKeepingTheRawValue() {
        String key = RateLimitKeyHasher.forAccount("account-123");

        assertThat(key).startsWith("account:").doesNotContain("account-123");
        assertThat(key.substring("account:".length())).hasSize(64);
    }

    @Test
    void normalizesEmailBeforeHashing() {
        assertThat(RateLimitKeyHasher.forEmail(" Person@Example.COM "))
                .isEqualTo(RateLimitKeyHasher.forEmail("person@example.com"))
                .doesNotContain("person@example.com");
    }
}
