package com.example.apiwatch.controller;

import com.example.apiwatch.config.SecurityConfig;
import com.example.apiwatch.dto.AuthenticationResponse;
import com.example.apiwatch.dto.RegisterUserRequest;
import com.example.apiwatch.dto.UserResponse;
import com.example.apiwatch.exception.DuplicateResourceException;
import com.example.apiwatch.exception.GlobalExceptionHandler;
import com.example.apiwatch.exception.InvalidCredentialsException;
import com.example.apiwatch.service.AuthenticationService;
import com.example.apiwatch.service.UserRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthenticationController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService registrationService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String REGISTER_BODY = """
            {
              "fullName": "API Watch User",
              "email": "user@example.com",
              "password": "ApiWatchPassword@2026"
            }
            """;

    private static final String LOGIN_BODY = """
            {
              "email": "user@example.com",
              "password": "ApiWatchPassword@2026"
            }
            """;

    @Test
    void registrationIsPublicAndReturnsCreatedUserWithoutPassword()
            throws Exception {
        UUID userId = UUID.randomUUID();

        when(registrationService.register(any()))
                .thenReturn(new UserResponse(
                        userId,
                        "API Watch User",
                        "user@example.com",
                        true,
                        Instant.parse("2026-10-02T02:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(registrationService).register(
                new RegisterUserRequest(
                        "API Watch User",
                        "user@example.com",
                        "ApiWatchPassword@2026"
                )
        );
    }

    @Test
    void loginIsPublicAndReturnsAuthenticationResponse()
            throws Exception {
        UUID userId = UUID.randomUUID();

        when(authenticationService.login(any()))
                .thenReturn(new AuthenticationResponse(
                        "test-token",
                        "Bearer",
                        Instant.parse("2026-10-02T03:00:00Z"),
                        userId,
                        "user@example.com"
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new DuplicateResourceException(
                        "Email address is already registered"
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Email address is already registered"
                ));
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        when(authenticationService.login(any()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        "Invalid email or password"
                ));
    }

    @Test
    void missingRegistrationFieldsReturnBadRequest()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());

        verifyNoInteractions(registrationService);
    }

    @Test
    void invalidEmailAndShortPasswordAreRejected()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "API Watch User",
                                  "email": "invalid-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    void blankLoginPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": " "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    void protectedPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordByteLimitErrorReturnsBadRequest()
            throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new IllegalArgumentException(
                        "Password cannot exceed 72 UTF-8 bytes"
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Password cannot exceed 72 UTF-8 bytes"
                ));
    }
}