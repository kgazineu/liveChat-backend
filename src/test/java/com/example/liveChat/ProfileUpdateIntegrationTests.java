package com.example.liveChat;

import com.example.liveChat.infra.mail.PasswordResetMailSender;
import com.example.liveChat.infra.mail.ProfileUpdateMailSender;
import com.example.liveChat.models.PasswordResetToken;
import com.example.liveChat.models.PendingProfileUpdate;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.PendingProfileUpdateRepository;
import com.example.liveChat.repositories.RefreshTokenRepository;
import com.example.liveChat.repositories.UserRepository;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProfileUpdateIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private PendingProfileUpdateRepository pendingProfileUpdates;
    @Autowired private PasswordResetTokenRepository passwordResetTokens;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ProfileUpdateMailSender profileUpdateMailSender;

    @MockitoBean
    private PasswordResetMailSender passwordResetMailSender;

    @BeforeEach
    void resetMailSenders() {
        reset(profileUpdateMailSender, passwordResetMailSender);
    }

    @Test
    void updateIsPendingAndMailGoesToOldAddressWhileOnlyTokenHashIsStored() throws Exception {
        User user = user();
        String accessToken = refreshTokenService.issue(user).token();
        String newEmail = ("NEW-" + UUID.randomUUID() + "@Example.Test").toLowerCase();

        mvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", "  Updated Name  ",
                                "email", newEmail.toUpperCase()))))
                .andExpect(status().isAccepted());

        var urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(profileUpdateMailSender).sendProfileUpdateConfirmation(eq(user.getEmail()), urlCaptor.capture());
        String rawToken = tokenFrom(urlCaptor.getValue());
        assertThat(rawToken).matches("[A-Za-z0-9_-]{43}");

        User unchanged = users.findById(user.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Test");
        assertThat(unchanged.getEmail()).isEqualTo(user.getEmail());

        PendingProfileUpdate stored = pendingFor(user);
        assertThat(stored.getRequestedName()).isEqualTo("Updated Name");
        assertThat(stored.getRequestedEmail()).isEqualTo(newEmail);
        assertThat(stored.getTokenHash()).isEqualTo(hash(rawToken)).doesNotContain(rawToken);
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void confirmationAppliesChangesOnceAndEmailChangeRevokesCredentialsAndPasswordResetTokens() throws Exception {
        User user = user();
        var oldLogin = refreshTokenService.issue(user);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(hash("R".repeat(43)));
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plusSeconds(300));
        passwordResetTokens.saveAndFlush(resetToken);
        String newEmail = "changed-" + UUID.randomUUID() + "@example.test";

        String profileToken = requestProfileToken(user, oldLogin.token(), Map.of(
                "name", "Changed", "email", newEmail));
        confirmProfile(profileToken).andExpect(status().isNoContent());

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Changed");
        assertThat(updated.getEmail()).isEqualTo(newEmail);
        assertThat(updated.getCredentialsVersion()).isEqualTo(1);
        assertThat(pendingProfileUpdates.findAll()).noneMatch(update -> update.getUser().getId().equals(user.getId()));
        assertThat(passwordResetTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(user.getId()));
        assertThat(refreshTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(user.getId()));

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + oldLogin.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", oldLogin.refreshToken()))))
                .andExpect(status().isUnauthorized());
        confirmProfile(profileToken).andExpect(status().isBadRequest());
    }

    @Test
    void previousRequestIsInvalidatedAndExpiredTokenCannotBeUsed() throws Exception {
        User user = user();
        String accessToken = refreshTokenService.issue(user).token();
        String firstToken = requestProfileToken(user, accessToken, Map.of("name", "First"));
        String secondToken = requestProfileToken(user, accessToken, Map.of("name", "Second"));

        assertThat(pendingProfileUpdates.findAll())
                .filteredOn(update -> update.getUser().getId().equals(user.getId()))
                .hasSize(1);
        confirmProfile(firstToken).andExpect(status().isBadRequest());
        confirmProfile(secondToken).andExpect(status().isNoContent());
        assertThat(users.findById(user.getId()).orElseThrow().getName()).isEqualTo("Second");

        User anotherUser = user();
        String expiredRawToken = "E".repeat(43);
        PendingProfileUpdate expired = new PendingProfileUpdate();
        expired.setTokenHash(hash(expiredRawToken));
        expired.setUser(anotherUser);
        expired.setRequestedName("Should Not Apply");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        pendingProfileUpdates.saveAndFlush(expired);

        confirmProfile(expiredRawToken).andExpect(status().isBadRequest());
        assertThat(users.findById(anotherUser.getId()).orElseThrow().getName()).isEqualTo("Test");
    }

    @Test
    void validatesEffectiveChangesAndDetectsEmailConflictAgainOnConfirmation() throws Exception {
        User user = user();
        String accessToken = refreshTokenService.issue(user).token();

        requestProfile(accessToken, Map.of()).andExpect(status().isBadRequest());
        requestProfile(accessToken, Map.of("name", "  Test  ")).andExpect(status().isBadRequest());
        requestProfile(accessToken, Map.of("name", "   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Name must be between 1 and 100 characters"));
        requestProfile(accessToken, Map.of("name", "x".repeat(101))).andExpect(status().isBadRequest());
        requestProfile(accessToken, Map.of("email", "not-an-email")).andExpect(status().isBadRequest());

        User existing = user();
        requestProfile(accessToken, Map.of("email", existing.getEmail().toUpperCase()))
                .andExpect(status().isConflict());

        String claimedLater = "claimed-" + UUID.randomUUID() + "@example.test";
        String profileToken = requestProfileToken(user, accessToken, Map.of("email", claimedLater));
        users.saveAndFlush(new User("Claimant", claimedLater, passwordEncoder.encode("password")));

        confirmProfile(profileToken).andExpect(status().isConflict());
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void authenticatedPasswordResetUsesPrincipalEmailAndConfirmationRemovesPendingUpdate() throws Exception {
        User user = user();
        var login = refreshTokenService.issue(user);
        requestProfileToken(user, login.token(), Map.of("name", "Pending"));

        mvc.perform(post("/users/me/password-reset")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isAccepted());

        var urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(passwordResetMailSender).sendPasswordReset(eq(user.getEmail()), urlCaptor.capture());
        String resetToken = tokenFrom(urlCaptor.getValue());
        mvc.perform(post("/users/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "password", "new-password"))))
                .andExpect(status().isNoContent());

        assertThat(pendingProfileUpdates.findAll()).noneMatch(update -> update.getUser().getId().equals(user.getId()));
        assertThat(passwordEncoder.matches("new-password", users.findById(user.getId()).orElseThrow().getPassword()))
                .isTrue();
    }

    @Test
    void protectedRequestsRejectAnonymousCallersAndDeletionRemovesPendingUpdate() throws Exception {
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/me/password-reset"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profileUpdateMailSender, passwordResetMailSender);

        User user = user();
        var login = refreshTokenService.issue(user);
        requestProfileToken(user, login.token(), Map.of("name", "Pending"));
        mvc.perform(delete("/users/" + user.getId()).header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isNoContent());

        assertThat(users.existsById(user.getId())).isFalse();
        assertThat(pendingProfileUpdates.findAll()).noneMatch(update -> update.getUser().getId().equals(user.getId()));
    }

    private org.springframework.test.web.servlet.ResultActions requestProfile(String accessToken, Map<String, String> body)
            throws Exception {
        return mvc.perform(put("/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private String requestProfileToken(User user, String accessToken, Map<String, String> body) throws Exception {
        requestProfile(accessToken, body).andExpect(status().isAccepted());
        var urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(profileUpdateMailSender, org.mockito.Mockito.atLeastOnce())
                .sendProfileUpdateConfirmation(eq(user.getEmail()), urlCaptor.capture());
        return tokenFrom(urlCaptor.getAllValues().getLast());
    }

    private org.springframework.test.web.servlet.ResultActions confirmProfile(String token) throws Exception {
        return mvc.perform(post("/users/profile-update/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", token))));
    }

    private PendingProfileUpdate pendingFor(User user) {
        return pendingProfileUpdates.findAll().stream()
                .filter(update -> update.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    private String tokenFrom(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("token");
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }
}
