package com.example.liveChat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.RefreshTokenRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.RefreshTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class SecurityIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private TokenService tokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void loginAndRefreshRotateTokensAndRejectReuse() throws Exception {
        User user = user();
        String result = mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("name").value("Test"))
                .andExpect(jsonPath("expiresIn").exists()).andExpect(jsonPath("refreshExpiresIn").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode login = mapper.readTree(result);
        String rawToken = login.get("refreshToken").asText();
        assertThat(refreshTokens.findAll()).noneMatch(token -> token.getTokenHash().equals(rawToken));
        JsonNode refreshed = mapper.readTree(mvc.perform(post("/users/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", rawToken))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(rawToken);
        assertThat(refreshed.get("token").asText()).isNotEqualTo(login.get("token").asText());
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + refreshed.get("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(user.getId()));
        refreshUnauthorized(rawToken);
        // Neither token can substitute for the other kind of credential.
        refreshUnauthorized(login.get("token").asText());
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + refreshed.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsMissingMalformedUnknownAndExpiredTokens() throws Exception {
        mvc.perform(post("/users/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        refreshUnauthorized("invalid");
        refreshUnauthorized("A".repeat(43));
        User user = user();
        UserLoginResponseDTO login = refreshTokenService.issue(user);
        var stored = refreshTokens.findAll().stream().filter(token -> token.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokens.saveAndFlush(stored);
        refreshUnauthorized(login.refreshToken());
    }

    @Test
    void simultaneousRefreshOnlySucceedsOnce() throws Exception {
        String rawToken = refreshTokenService.issue(user()).refreshToken();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> refresh = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                refreshTokenService.refresh(rawToken);
                return true;
            } catch (BadCredentialsException exception) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(refresh);
            var second = executor.submit(refresh);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void deletionOnlyAllowsOwnAccountAndInvalidatesCredentials() throws Exception {
        User owner = user();
        User other = user();
        UserLoginResponseDTO login = refreshTokenService.issue(owner);
        mvc.perform(delete("/users/" + other.getId()).header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isForbidden());
        assertThat(users.existsById(other.getId())).isTrue();
        mvc.perform(delete("/users/" + owner.getId()).header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isNoContent());
        assertThat(users.existsById(owner.getId())).isFalse();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isUnauthorized());
        refreshUnauthorized(login.refreshToken());
    }

    @Test
    void protectedRoutesRejectMissingMalformedExpiredOrIncorrectlyPrefixedCredentials() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        String token = tokenService.generateToken(user()).token();
        for (String header : List.of("Bearer invalid", token, "Basic " + token, "Bearer ")) {
            mvc.perform(get("/users/me").header("Authorization", header)).andExpect(status().isUnauthorized());
        }
        String expired = JWT.create().withIssuer("liveChat").withSubject(user().getEmail())
                .withExpiresAt(Instant.now().minusSeconds(10))
                .sign(Algorithm.HMAC256("test-only-secret-at-least-32-characters-long"));
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginReturnsUnauthorized() throws Exception {
        User user = user();
        for (String email : List.of(user.getEmail(), "missing@example.test")) {
            mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("email", email, "password", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void healthIsPublicAndDoesNotExposeDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(content().string("{\"status\":\"UP\"}"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocumentationIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("LiveChat API"));
        mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("openapi: 3.0.3"),
                        org.hamcrest.Matchers.containsString("/direct-channels:"))));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/openapi.yaml"));
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }

    private void refreshUnauthorized(String token) throws Exception {
        mvc.perform(post("/users/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().isUnauthorized());
    }
}
