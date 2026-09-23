package com.example.liveChat.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class TokenService {

    private final String secret;

    public TokenService(@Value("${JWT_SECRET}") String secret) {
        this.secret = secret;
    }

    public UserLoginResponseDTO generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            Instant expirationDate = genExpirationDate();

            String token = JWT.create()
                    .withIssuer("liveChat")
                    .withSubject(user.getEmail())
                    .withClaim("cv", user.getCredentialsVersion())
                    .withJWTId(UUID.randomUUID().toString())
                    .withExpiresAt(expirationDate)
                    .sign(algorithm);
            return new UserLoginResponseDTO(user.getName(), token, expirationDate, null, null);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public String validateToken(String token){
        DecodedJWT decodedToken = verify(token);
        return decodedToken == null || decodedToken.getSubject() == null ? "" : decodedToken.getSubject();
    }

    public boolean isTokenValidForUser(String token, User user) {
        if (user == null) return false;
        DecodedJWT decodedToken = verify(token);
        if (decodedToken == null || !user.getEmail().equals(decodedToken.getSubject())) return false;
        Long credentialsVersion = decodedToken.getClaim("cv").asLong();
        return credentialsVersion != null && credentialsVersion == user.getCredentialsVersion();
    }

    private DecodedJWT verify(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("liveChat")
                    .build()
                    .verify(token);
        } catch (JWTVerificationException exception){
            return null;
        }
    }

    public Instant genExpirationDate() {
        return Instant.now().plus(2, ChronoUnit.HOURS);
    }
}
