package com.example.apiwatch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        @Size(max = 200)
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 72)
        String password
) {
}