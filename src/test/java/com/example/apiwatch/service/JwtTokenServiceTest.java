package com.example.apiwatch.security;

import com.example.apiwatch.config.JwtConfig;
import com.example.apiwatch.dto.GeneratedJwtToken;
import com.example.apiwatch.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET =
            "apiwatch-test-secret-at-least-32-bytes-long";

    private JwtEncoder encoder;
    private JwtDecoder decoder;
    private JwtTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        var key = config.jwtSecretKey(SECRET);

        encoder = config.jwtEncoder(key);
        decoder = config.jwtDecoder(key);
        service = new JwtTokenService(encoder);

        ReflectionTestUtils.setField(
                service,
                "expirationMinutes",
                60L
        );

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .active(true)
                .build();
    }

    @Test
    void generatedTokenHasExpectedClaimsAndLifetime() {
        GeneratedJwtToken generated = service.generateToken(user);

        Jwt jwt = decoder.decode(generated.token());

        assertAll(
                () -> assertEquals(
                        user.getId().toString(),
                        jwt.getSubject()
                ),
                () -> assertEquals(
                        "apiwatch",
                        jwt.getClaimAsString("iss")
                ),
                () -> assertEquals(
                        user.getEmail(),
                        jwt.getClaimAsString("email")
                ),
                () -> assertEquals(
                        jwt.getIssuedAt().plusSeconds(3600),
                        jwt.getExpiresAt()
                ),
                () -> assertEquals(
                        generated.expiresAt().getEpochSecond(),
                        jwt.getExpiresAt().getEpochSecond()
                )
        );
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        GeneratedJwtToken generated = service.generateToken(user);

        JwtConfig config = new JwtConfig();
        JwtDecoder otherDecoder = config.jwtDecoder(
                config.jwtSecretKey(
                        "another-test-secret-at-least-32-bytes-long"
                )
        );

        assertThrows(
                JwtException.class,
                () -> otherDecoder.decode(generated.token())
        );
    }

    @Test
    void modifiedSignatureIsRejected() {
        String token = service.generateToken(user).token();

        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';

        String modified = token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);

        assertThrows(
                JwtException.class,
                () -> decoder.decode(modified)
        );
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();

        String token = signToken(
                "apiwatch",
                now.minusSeconds(7200),
                now.minusSeconds(3600)
        );

        assertThrows(
                JwtException.class,
                () -> decoder.decode(token)
        );
    }

    @Test
    void incorrectIssuerIsRejected() {
        Instant now = Instant.now();

        String token = signToken(
                "another-application",
                now,
                now.plusSeconds(3600)
        );

        assertThrows(
                JwtException.class,
                () -> decoder.decode(token)
        );
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtConfig().jwtSecretKey("too-short")
        );
    }

    @Test
    void nonPositiveTokenLifetimeIsRejected() {
        ReflectionTestUtils.setField(
                service,
                "expirationMinutes",
                0L
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.generateToken(user)
        );
    }

    private String signToken(
            String issuer,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}