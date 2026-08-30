package org.aisa.api.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** The request and response shapes of the account endpoints. */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * Completes signup for a caller who already holds a Firebase account.
     *
     * <p>No password field: the password went to Firebase and never touches this API.
     * There is nothing here we could leak in a log line.
     */
    public record RegisterRequest(
            @NotBlank(message = "Your name is required")
            @Size(max = 120, message = "Name is too long")
            String name,

            @Size(max = 40, message = "Roll number is too long")
            String rollNumber,

            @Min(value = 1, message = "Year must be between 1 and 4")
            @Max(value = 4, message = "Year must be between 1 and 4")
            Integer year) {}

    /** What a student may change about themselves. Note that role and status are absent. */
    public record ProfileRequest(
            @NotBlank(message = "Your name is required")
            @Size(max = 120, message = "Name is too long")
            String name,

            @Size(max = 40, message = "Roll number is too long")
            String rollNumber,

            @Min(value = 1, message = "Year must be between 1 and 4")
            @Max(value = 4, message = "Year must be between 1 and 4")
            Integer year,

            String photoUrl,
            String photoPublicId) {}

    /**
     * The signed-in caller, as the frontend needs them.
     *
     * <p>{@code state} is the field the whole account UI turns on. It exists because
     * "signed in" is not one condition: a caller can hold a perfectly valid token and
     * still be unregistered, unverified or suspended, and each needs a different screen.
     * Returning a single boolean here would push the frontend into guessing from status
     * codes.
     */
    public record MeResponse(
            String uid,
            String email,
            String name,
            String role,
            String state,
            boolean emailVerified,
            String rollNumber,
            Integer year,
            String photoUrl,
            Instant lastLoginAt) {}

    /** A row in the admin user list. */
    public record UserSummary(
            String uid,
            String email,
            String name,
            String role,
            String status,
            String rollNumber,
            Integer year,
            String photoUrl,
            Instant lastLoginAt,
            Instant createdAt) {}

    /**
     * An admin changing someone's role or suspending them.
     *
     * <p>Both fields are optional so one PATCH can do either without the caller having to
     * echo back the value it is not changing — and echoing it back is exactly how a stale
     * dashboard tab silently reverts someone's role.
     */
    public record UpdateUserRequest(
            @Pattern(regexp = "STUDENT|ADMIN", message = "Role must be STUDENT or ADMIN")
            String role,

            @Pattern(regexp = "ACTIVE|SUSPENDED", message = "Status must be ACTIVE or SUSPENDED")
            String status) {}

}
