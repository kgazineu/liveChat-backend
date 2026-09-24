package com.example.liveChat;

import com.example.liveChat.models.User;
import com.example.liveChat.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AbuseRateLimitIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void registrationAllowsFiveRequestsPerHourPerIp() throws Exception {
        String clientIp = "10.40.0.1";
        for (int request = 0; request < 5; request++) {
            mvc.perform(withRemoteAddress(post("/users/register"), clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "name", "Rate Limited User",
                                    "email", UUID.randomUUID() + "@example.test",
                                    "password", "password"))))
                    .andExpect(status().isOk());
        }

        mvc.perform(withRemoteAddress(post("/users/register"), clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", "Blocked User",
                                "email", UUID.randomUUID() + "@example.test",
                                "password", "password"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void loginLimitsBothAccountAndIpAttempts() throws Exception {
        User user = users.save(new User("Login Target", UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("password")));

        for (int request = 0; request < 5; request++) {
            mvc.perform(login(user.getEmail(), "10.41.0." + (request + 1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(user.getEmail(), "10.41.0.20"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        String sharedIp = "10.42.0.1";
        for (int request = 0; request < 10; request++) {
            mvc.perform(login(UUID.randomUUID() + "@example.test", sharedIp))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(UUID.randomUUID() + "@example.test", sharedIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void passwordResetLimitsBothEmailAndIpWithoutRequiringAnExistingAccount() throws Exception {
        String targetEmail = UUID.randomUUID() + "@example.test";
        mvc.perform(passwordReset(targetEmail, "10.43.0.1")).andExpect(status().isAccepted());
        mvc.perform(passwordReset(targetEmail, "10.43.0.2")).andExpect(status().isAccepted());
        mvc.perform(passwordReset(targetEmail, "10.43.0.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        String sharedIp = "10.44.0.1";
        for (int request = 0; request < 3; request++) {
            mvc.perform(passwordReset(UUID.randomUUID() + "@example.test", sharedIp))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(passwordReset(UUID.randomUUID() + "@example.test", sharedIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private MockHttpServletRequestBuilder login(String email, String clientIp) throws Exception {
        return withRemoteAddress(post("/users/login"), clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "wrong-password")));
    }

    private MockHttpServletRequestBuilder passwordReset(String email, String clientIp) throws Exception {
        return withRemoteAddress(post("/users/password-reset/request"), clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email)));
    }

    private MockHttpServletRequestBuilder withRemoteAddress(MockHttpServletRequestBuilder request, String clientIp) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(clientIp);
            return servletRequest;
        });
    }
}
