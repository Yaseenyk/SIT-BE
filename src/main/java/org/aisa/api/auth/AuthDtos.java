package org.aisa.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request and response bodies for {@code /api/v1/auth}.
 *
 * <p>Grouped in one file because they are small, always change together, and are only
 * ever used by {@link AuthController} — nine separate files would be nine places to look.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password) {}

    public record LoginResponse(
            String token,
            String tokenType,
            long expiresInSeconds,
            String username) {}

    public record MeResponse(
            String username,
            Instant lastLoginAt) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            /*
             * 10 characters, not 8. This password protects every write on a public site
             * and is typed a handful of times a year, so length costs almost nothing.
             * Composition rules are deliberately absent — they push people towards
             * "Password1!" and no further.
             */
            @NotBlank(message = "New password is required")
            @Size(min = 10, max = 100, message = "New password must be at least 10 characters")
            String newPassword) {}

    public record ChangeUsernameRequest(
            @NotBlank(message = "Password is required") String currentPassword,
            @NotBlank(message = "New username is required")
            @Size(min = 3, max = 64, message = "Username must be 3-64 characters")
            String newUsername) {}
}
