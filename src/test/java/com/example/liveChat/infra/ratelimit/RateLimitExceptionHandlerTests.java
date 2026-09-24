package com.example.liveChat.infra.ratelimit;

import com.example.liveChat.exceptions.RateLimitExceededException;
import com.example.liveChat.infra.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitExceptionHandlerTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void returnsTooManyRequestsWithRetryAfter() throws Exception {
        mockMvc.perform(get("/rate-limit-test/exceeded"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.status").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Try again later."));
    }

    @Test
    void returnsSafeServiceUnavailableErrorForInfrastructureFailure() throws Exception {
        mockMvc.perform(get("/rate-limit-test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Rate limiting service is temporarily unavailable"));
    }

    @RestController
    private static class ThrowingController {
        @GetMapping("/rate-limit-test/exceeded")
        void exceeded() {
            throw new RateLimitExceededException(7);
        }

        @GetMapping("/rate-limit-test/unavailable")
        void unavailable() {
            throw new RateLimitInfrastructureException(new RuntimeException("sensitive Redis details"));
        }
    }
}
