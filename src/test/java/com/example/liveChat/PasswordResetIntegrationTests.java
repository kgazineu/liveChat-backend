package com.example.liveChat;

import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.infra.mail.PasswordResetMailSender;
import com.example.liveChat.models.PasswordResetToken;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.RefreshTokenRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.PasswordResetService;
import com.example.liveChat.services.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PasswordResetIntegrationTests {
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordResetTokenRepository passwordResetTokens;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PasswordResetMailSender mailSender;

    private String clientIp;

    @BeforeEach
    void resetMailSender() {
        reset(mailSender);
        int sequence = CLIENT_SEQUENCE.incrementAndGet();
        clientIp = "10.20." + (sequence / 250) + "." + (sequence % 250 + 1);
    }

    @Test
    void requestAlwaysReturnsAcceptedWithoutAccountEnumerationAndStoresOnlyHash() throws Exception {
        String missingEmail = UUID.randomUUID() + "@example.test";
        request(missingEmail).andExpect(status().isAccepted());
        request(null).andExpect(status().isAccepted());
        verifyNoInteractions(mailSender);

        User user = user();
        request(user.getEmail()).andExpect(status().isAccepted());

        var urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendPasswordReset(eq(user.getEmail()), urlCaptor.capture());
        String rawToken = tokenFrom(urlCaptor.getValue());
        assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");

        PasswordResetToken stored = passwordResetTokens.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(stored.getTokenHash()).isEqualTo(hash(rawToken)).doesNotContain(rawToken);
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void sendsMailOnlyAfterTheTokenTransactionCommits() {
        User user = user();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            passwordResetService.request(user.getEmail());
            verifyNoInteractions(mailSender);
            assertThat(passwordResetTokens.findAll())
                    .anyMatch(token -> token.getUser().getId().equals(user.getId()));
        });

        verify(mailSender).sendPasswordReset(eq(user.getEmail()), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rollbackDoesNotSendMailOrPersistTheToken() {
        User user = user();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            passwordResetService.request(user.getEmail());
            verifyNoInteractions(mailSender);
            status.setRollbackOnly();
        });

        verifyNoInteractions(mailSender);
        assertThat(passwordResetTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(user.getId()));
    }

    @Test
    void mailFailureStillReturnsAcceptedAndDoesNotLeaveAnUnsentToken() throws Exception {
        User user = user();
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(mailSender).sendPasswordReset(eq(user.getEmail()), org.mockito.ArgumentMatchers.anyString());

        request(user.getEmail()).andExpect(status().isAccepted());
        assertThat(passwordResetTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(user.getId()));
    }

    @Test
    void confirmChangesPasswordAndRevokesEveryCredentialAndResetToken() throws Exception {
        User user = user();
        var oldLogin = refreshTokenService.issue(user);
        String firstResetToken = requestToken(user);
        String secondResetToken = requestToken(user);
        assertThat(passwordResetTokens.findAll()).filteredOn(token -> token.getUser().getId().equals(user.getId()))
                .singleElement()
                .extracting(PasswordResetToken::getTokenHash)
                .isEqualTo(hash(secondResetToken));
        confirm(firstResetToken, "another-password").andExpect(status().isBadRequest());

        mvc.perform(post("/users/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", secondResetToken, "password", "new-password"))))
                .andExpect(status().isNoContent());

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getCredentialsVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches("new-password", updated.getPassword())).isTrue();
        assertThat(passwordResetTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(user.getId()));
        assertThat(refreshTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(user.getId()));

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + oldLogin.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", oldLogin.refreshToken()))))
                .andExpect(status().isUnauthorized());
        login(user.getEmail(), "password").andExpect(status().isUnauthorized());
        login(user.getEmail(), "new-password").andExpect(status().isOk());

        confirm(firstResetToken, "another-password").andExpect(status().isBadRequest());
        confirm(secondResetToken, "another-password").andExpect(status().isBadRequest());
    }

    @Test
    void confirmRejectsMalformedUnknownExpiredTokensAndInvalidPasswords() throws Exception {
        User user = user();
        confirm("invalid", "new-password").andExpect(status().isBadRequest());
        confirm("A".repeat(43), "new-password").andExpect(status().isBadRequest());

        String expiredRawToken = "B".repeat(43);
        PasswordResetToken expired = new PasswordResetToken();
        expired.setTokenHash(hash(expiredRawToken));
        expired.setUser(user);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        passwordResetTokens.saveAndFlush(expired);
        confirm(expiredRawToken, "new-password").andExpect(status().isBadRequest());

        String validToken = requestToken(user);
        confirm(validToken, "short").andExpect(status().isBadRequest());
        confirm(validToken, "x".repeat(73)).andExpect(status().isBadRequest());
        assertThat(passwordEncoder.matches("password", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void sameTokenCanOnlyBeConsumedOnceUnderConcurrency() throws Exception {
        User user = user();
        String rawToken = requestToken(user);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> confirm = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                passwordResetService.confirm(rawToken, "new-password");
                return true;
            } catch (InvalidRequestException exception) {
                return false;
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(confirm);
            var second = executor.submit(confirm);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void registrationAndResetSharePasswordLengthPolicy() throws Exception {
        for (String password : List.of("short", "x".repeat(73))) {
            mvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "name", "Test", "email", UUID.randomUUID() + "@example.test", "password", password))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Password must be between 8 and 72 characters"));
        }
    }

    private org.springframework.test.web.servlet.ResultActions request(String email) throws Exception {
        Map<String, String> body = email == null ? Map.of() : Map.of("email", email);
        return mvc.perform(post("/users/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .content(mapper.writeValueAsString(body)));
    }

    private String requestToken(User user) throws Exception {
        request(user.getEmail()).andExpect(status().isAccepted());
        var urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce())
                .sendPasswordReset(eq(user.getEmail()), urlCaptor.capture());
        return tokenFrom(urlCaptor.getAllValues().getLast());
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String password) throws Exception {
        return mvc.perform(post("/users/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", token, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String tokenFrom(String resetUrl) {
        return UriComponentsBuilder.fromUriString(resetUrl).build().getQueryParams().getFirst("token");
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }
}
