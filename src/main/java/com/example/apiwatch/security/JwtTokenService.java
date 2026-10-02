package com.example.apiwatch.security;

import com.example.apiwatch.dto.GeneratedJwtToken;
import com.example.apiwatch.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${security.jwt.expiration-minutes:60}")
    private long expirationMinutes;

    public GeneratedJwtToken generateToken(User user) {
        if (expirationMinutes <= 0) {
            throw new IllegalStateException(
                    "JWT expiration must be positive"
            );
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                expirationMinutes,
                ChronoUnit.MINUTES
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("apiwatch")
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new GeneratedJwtToken(token, expiresAt);
    }
}