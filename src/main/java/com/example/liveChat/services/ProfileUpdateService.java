package com.example.liveChat.services;

import com.example.liveChat.dto.ProfileUpdateRequestDTO;
import com.example.liveChat.exceptions.InvalidRequestException;
import com.example.liveChat.exceptions.UserAlreadyExistsException;
import com.example.liveChat.exceptions.UserNotFoundException;
import com.example.liveChat.infra.mail.ProfileUpdateMailSender;
import com.example.liveChat.models.PendingProfileUpdate;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.PendingProfileUpdateRepository;
import com.example.liveChat.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProfileUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(ProfileUpdateService.class);
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "Invalid, expired or already used profile update token";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final PendingProfileUpdateRepository pendingProfileUpdateRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final ProfileUpdateMailSender mailSender;
    private final EntityManager entityManager;
    private final Duration tokenTtl;
    private final String frontendProfileUpdateUrl;

    public ProfileUpdateService(UserRepository userRepository,
                                PendingProfileUpdateRepository pendingProfileUpdateRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                RefreshTokenService refreshTokenService,
                                ProfileUpdateMailSender mailSender,
                                EntityManager entityManager,
                                @Value("${livechat.profile-update.token-ttl:15m}") Duration tokenTtl,
                                @Value("${livechat.frontend.profile-update-url}") String frontendProfileUpdateUrl) {
        this.userRepository = userRepository;
        this.pendingProfileUpdateRepository = pendingProfileUpdateRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.mailSender = mailSender;
        this.entityManager = entityManager;
        this.tokenTtl = tokenTtl;
        this.frontendProfileUpdateUrl = frontendProfileUpdateUrl;
    }

    @Transactional
    public void request(String userId, ProfileUpdateRequestDTO request) {
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null || !user.isEnabled()) throw new UserNotFoundException("User not found");

        String requestedName = normalizeName(request == null ? null : request.name());
        String requestedEmail = normalizeEmail(request == null ? null : request.email());

        if (requestedName != null && requestedName.equals(user.getName())) requestedName = null;
        if (requestedEmail != null && requestedEmail.equalsIgnoreCase(user.getEmail())) requestedEmail = null;
        if (requestedName == null && requestedEmail == null) {
            throw new InvalidRequestException("At least one profile field must be different");
        }
        if (requestedEmail != null) ensureEmailAvailable(requestedEmail, user.getId());

        pendingProfileUpdateRepository.deleteByUserId(user.getId());
        pendingProfileUpdateRepository.flush();

        String rawToken = generateToken();
        PendingProfileUpdate pendingUpdate = new PendingProfileUpdate();
        pendingUpdate.setTokenHash(hash(rawToken));
        pendingUpdate.setUser(user);
        pendingUpdate.setRequestedName(requestedName);
        pendingUpdate.setRequestedEmail(requestedEmail);
        pendingUpdate.setExpiresAt(Instant.now().plus(tokenTtl));
        pendingProfileUpdateRepository.saveAndFlush(pendingUpdate);

        try {
            mailSender.sendProfileUpdateConfirmation(user.getEmail(), confirmationUrl(rawToken));
        } catch (RuntimeException exception) {
            pendingProfileUpdateRepository.delete(pendingUpdate);
            logger.error("Could not send profile update confirmation e-mail", exception);
        }
    }

    @Transactional
    public void confirm(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();

        String tokenHash = hash(rawToken);
        String userId = pendingProfileUpdateRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null || !user.isEnabled()) throw invalidToken();

        PendingProfileUpdate pendingUpdate = pendingProfileUpdateRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        if (!pendingUpdate.getExpiresAt().isAfter(Instant.now())) throw invalidToken();

        String requestedEmail = pendingUpdate.getRequestedEmail();
        boolean emailChanged = requestedEmail != null && !requestedEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanged) ensureEmailAvailable(requestedEmail, user.getId());

        if (pendingUpdate.getRequestedName() != null) user.setName(pendingUpdate.getRequestedName());
        if (emailChanged) {
            user.setEmail(requestedEmail);
            user.setCredentialsVersion(user.getCredentialsVersion() + 1);
            passwordResetTokenRepository.deleteByUserId(user.getId());
            refreshTokenService.revokeAllForUser(user.getId());
        }
        pendingProfileUpdateRepository.deleteByUserId(user.getId());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw emailConflict(requestedEmail);
        }
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new InvalidRequestException("Name must be between 1 and 100 characters");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRequestException("Invalid email");
        }
        return normalized;
    }

    private void ensureEmailAvailable(String email, String userId) {
        userRepository.findActiveByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> { throw emailConflict(email); });
    }

    private UserAlreadyExistsException emailConflict(String email) {
        return new UserAlreadyExistsException("A user with email " + email + " already exists");
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

    private String confirmationUrl(String rawToken) {
        return UriComponentsBuilder.fromUriString(frontendProfileUpdateUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }

    private InvalidRequestException invalidToken() {
        return new InvalidRequestException(INVALID_TOKEN_MESSAGE);
    }
}
