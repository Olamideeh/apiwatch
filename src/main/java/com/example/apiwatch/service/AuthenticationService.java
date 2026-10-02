package com.example.apiwatch.service;

import com.example.apiwatch.dto.AuthenticationResponse;
import com.example.apiwatch.dto.GeneratedJwtToken;
import com.example.apiwatch.dto.LoginRequest;
import com.example.apiwatch.entity.User;
import com.example.apiwatch.exception.InvalidCredentialsException;
import com.example.apiwatch.repository.UserRepository;
import com.example.apiwatch.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        GeneratedJwtToken token = jwtTokenService.generateToken(user);

        return new AuthenticationResponse(
                token.token(),
                "Bearer",
                token.expiresAt(),
                user.getId(),
                user.getEmail()
        );
    }
}