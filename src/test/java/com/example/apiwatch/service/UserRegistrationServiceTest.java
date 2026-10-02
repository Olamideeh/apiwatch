package com.example.apiwatch.service;

import com.example.apiwatch.dto.RegisterUserRequest;
import com.example.apiwatch.dto.UserResponse;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.exception.DuplicateResourceException;
import com.example.apiwatch.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void registrationReturnsSavedUserDetails() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-02T02:00:00Z");

        when(passwordEncoder.encode("ApiWatchPassword@2026"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(userId);
                    user.setCreatedAt(createdAt);
                    return user;
                });

        UserResponse response = service.register(validRequest());

        assertAll(
                () -> assertEquals(userId, response.id()),
                () -> assertEquals("API Watch User", response.fullName()),
                () -> assertEquals("user@example.com", response.email()),
                () -> assertTrue(response.active()),
                () -> assertEquals(createdAt, response.createdAt())
        );
    }

    @Test
    void normalizesNameAndEmailBeforeDuplicateCheckAndSave() {
        when(passwordEncoder.encode(anyString()))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new RegisterUserRequest(
                "  API Watch User  ",
                "  USER@Example.COM  ",
                "ApiWatchPassword@2026"
        ));

        verify(userRepository).existsByEmail("user@example.com");

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        assertAll(
                () -> assertEquals(
                        "API Watch User",
                        captor.getValue().getFullName()
                ),
                () -> assertEquals(
                        "user@example.com",
                        captor.getValue().getEmail()
                )
        );
    }

    @Test
    void duplicateEmailIsRejectedWithoutEncodingOrSaving() {
        when(userRepository.existsByEmail("user@example.com"))
                .thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> service.register(validRequest())
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void savesEncodedPasswordAndPreservesPasswordWhitespace() {
        String password = "  ApiWatchPassword@2026  ";

        when(passwordEncoder.encode(password))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new RegisterUserRequest(
                "API Watch User",
                "user@example.com",
                password
        ));

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());
        verify(passwordEncoder).encode(password);

        assertEquals(
                "encoded-password",
                captor.getValue().getPasswordHash()
        );
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void rejectsPasswordExceeding72Utf8Bytes() {
        // 37 characters, but 74 bytes when encoded as UTF-8.
        String password = "é".repeat(37);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.register(new RegisterUserRequest(
                        "API Watch User",
                        "user@example.com",
                        password
                ))
        );

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    private RegisterUserRequest validRequest() {
        return new RegisterUserRequest(
                "API Watch User",
                "user@example.com",
                "ApiWatchPassword@2026"
        );
    }
}