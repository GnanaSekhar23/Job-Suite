package com.jobsuite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name too long")
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name too long")
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100,
                message = "Password must be at least 8 characters")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshTokenRequest {

        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private UserResponse user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponse {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String profilePictureUrl;
        private String role;
        private String provider;
        private boolean emailVerified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoogleLoginRequest {
        private String idToken;
    }



}