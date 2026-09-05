package com.example.liveChat.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
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
                    .withJWTId(UUID.randomUUID().toString())
                    .withExpiresAt(expirationDate)
                    .sign(algorithm);
            return new UserLoginResponseDTO(user.getName(), token, expirationDate, null, null);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public String validateToken(String token){
        if (token == null || token.isBlank()) return "";
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String subject = JWT.require(algorithm)
                    .withIssuer("liveChat")
                    .build()
                    .verify(token)
                    .getSubject();
            return subject == null ? "" : subject;
        } catch (JWTVerificationException exception){
            return "";
        }
    }

    public Instant genExpirationDate() {
        return Instant.now().plus(2, ChronoUnit.HOURS);
    }
}
