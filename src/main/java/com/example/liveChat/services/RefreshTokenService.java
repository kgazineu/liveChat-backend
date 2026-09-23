package com.example.liveChat.services;

import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.RefreshToken;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenService tokenService;

    @Transactional
    public UserLoginResponseDTO issue(User user) {
        if (user == null || !user.isEnabled()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        return rotate(refreshToken);
    }

    @Transactional
    public void revokeAllForUser(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public UserLoginResponseDTO refresh(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (!refreshToken.getExpiresAt().isAfter(Instant.now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        return rotate(refreshToken);
    }

    private UserLoginResponseDTO rotate(RefreshToken refreshToken) {
        if (!refreshToken.getUser().isEnabled()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadCredentialsException("Invalid refresh token");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
        UserLoginResponseDTO accessToken = tokenService.generateToken(refreshToken.getUser());
        return new UserLoginResponseDTO(accessToken.name(), accessToken.token(), accessToken.expiresIn(),
                rawToken, refreshToken.getExpiresAt());
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
