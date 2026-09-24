package com.example.liveChat.services;

import com.example.liveChat.exceptions.InvalidRequestException;

import com.example.liveChat.models.PasswordResetToken;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.PendingProfileUpdateRepository;
import com.example.liveChat.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "Invalid, expired or already used password reset token";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PendingProfileUpdateRepository pendingProfileUpdateRepository;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetPasswordPolicy passwordPolicy;
    private final EntityManager entityManager;
    private final Duration tokenTtl;
    private final String frontendResetUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                PendingProfileUpdateRepository pendingProfileUpdateRepository,
                                RefreshTokenService refreshTokenService,
                                ApplicationEventPublisher eventPublisher,
                                PasswordEncoder passwordEncoder,
                                PasswordResetPasswordPolicy passwordPolicy,
                                EntityManager entityManager,
                                @Value("${livechat.password-reset.token-ttl:15m}") Duration tokenTtl,
                                @Value("${livechat.frontend.password-reset-url}") String frontendResetUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.pendingProfileUpdateRepository = pendingProfileUpdateRepository;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.entityManager = entityManager;
        this.tokenTtl = tokenTtl;
        this.frontendResetUrl = frontendResetUrl;
    }

    @Transactional
    public void request(String email) {
        if (email == null || email.isBlank()) return;

        userRepository.findActiveByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            String rawToken = generateToken();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setTokenHash(hash(rawToken));
            resetToken.setUser(user);
            resetToken.setExpiresAt(Instant.now().plus(tokenTtl));

            passwordResetTokenRepository.deleteByUserId(user.getId());
            passwordResetTokenRepository.saveAndFlush(resetToken);
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                    user.getEmail(), resetUrl(rawToken), resetToken.getId()));
        });
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        passwordPolicy.validate(newPassword);
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }

        String tokenHash = hash(rawToken);
        String userId = passwordResetTokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null || !user.isEnabled()) throw invalidToken();

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        if (!resetToken.getExpiresAt().isAfter(Instant.now())) {
            throw invalidToken();
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        passwordResetTokenRepository.deleteByUserId(user.getId());
        pendingProfileUpdateRepository.deleteByUserId(user.getId());
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String resetUrl(String rawToken) {
        return UriComponentsBuilder.fromUriString(frontendResetUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }

    private InvalidRequestException invalidToken() {
        return new InvalidRequestException(INVALID_TOKEN_MESSAGE);
    }
}
