package com.example.apiwatch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        @Size(max = 200)
        String email,

        @NotBlank(message = "Password is required")
        @Size(
                min = 12,
                max = 72,
                message = "Password must contain between 12 and 72 characters"
        )
        String password
) {
}