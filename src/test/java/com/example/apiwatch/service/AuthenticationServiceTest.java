package com.example.apiwatch.service;

import com.example.apiwatch.dto.AuthenticationResponse;
import com.example.apiwatch.dto.GeneratedJwtToken;
import com.example.apiwatch.dto.LoginRequest;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.exception.InvalidCredentialsException;
import com.example.apiwatch.repository.UserRepository;
import com.example.apiwatch.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthenticationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                userRepository,
                passwordEncoder,
                jwtTokenService
        );

        user = User.builder()
                .id(UUID.randomUUID())
                .fullName("API Watch User")
                .email("user@example.com")
                .passwordHash("stored-password-hash")
                .active(true)
                .build();
    }

    @Test
    void validCredentialsReturnTokenAndUserDetails() {
        Instant expiresAt = Instant.parse("2026-10-02T04:00:00Z");

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "ApiWatchPassword@2026",
                "stored-password-hash"
        )).thenReturn(true);

        when(jwtTokenService.generateToken(user))
                .thenReturn(new GeneratedJwtToken(
                        "generated-jwt",
                        expiresAt
                ));

        AuthenticationResponse response = service.login(
                validRequest()
        );

        assertAll(
                () -> assertEquals(
                        "generated-jwt", response.accessToken()
                ),
                () -> assertEquals("Bearer", response.tokenType()),
                () -> assertEquals(expiresAt, response.expiresAt()),
                () -> assertEquals(user.getId(), response.userId()),
                () -> assertEquals(user.getEmail(), response.email())
        );
    }

    @Test
    void normalizesEmailBeforeLookup() {
        stubSuccessfulLogin("ApiWatchPassword@2026");

        service.login(new LoginRequest(
                "  USER@Example.COM  ",
                "ApiWatchPassword@2026"
        ));

        verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    void unknownEmailIsRejectedWithoutGeneratingToken() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException error = assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(validRequest())
        );

        assertEquals("Invalid email or password", error.getMessage());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void incorrectPasswordIsRejectedWithoutGeneratingToken() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "ApiWatchPassword@2026",
                "stored-password-hash"
        )).thenReturn(false);

        InvalidCredentialsException error = assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(validRequest())
        );

        assertEquals("Invalid email or password", error.getMessage());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void inactiveUserCannotLogIn() {
        user.setActive(false);

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(validRequest())
        );

        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void preservesPasswordWhitespaceDuringVerification() {
        String password = "  ApiWatchPassword@2026  ";
        stubSuccessfulLogin(password);

        service.login(new LoginRequest(
                "user@example.com",
                password
        ));

        verify(passwordEncoder).matches(
                password,
                "stored-password-hash"
        );
    }

    private void stubSuccessfulLogin(String password) {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                password,
                "stored-password-hash"
        )).thenReturn(true);

        when(jwtTokenService.generateToken(user))
                .thenReturn(new GeneratedJwtToken(
                        "generated-jwt",
                        Instant.parse("2026-10-02T04:00:00Z")
                ));
    }

    private LoginRequest validRequest() {
        return new LoginRequest(
                "user@example.com",
                "ApiWatchPassword@2026"
        );
    }
}