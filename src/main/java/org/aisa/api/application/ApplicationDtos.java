package org.aisa.api.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplyRequest(
            @NotBlank(message = "Choose a committee") String committeeId,

            @NotBlank(message = "Tell us why you want to join")
            @Size(min = 20, max = 2000,
                    message = "Say a little more — between 20 and 2000 characters")
            String motivation) {}

    /**
     * An admin's decision.
     *
     * <p>{@code role} is the title the accepted student gets on the public roster, and it
     * is optional — accepting without one lists them as "Member", which is true and
     * editable, rather than blocking the accept on a field the admin may not have decided.
     */
    public record ReviewRequest(
            @NotBlank
            @Pattern(regexp = "ACCEPTED|REJECTED", message = "Decide either ACCEPTED or REJECTED")
            String status,

            @Size(max = 80, message = "Role is too long")
            String role) {}

    /** The student's own view: no reviewer identity, which is none of their business. */
    public record MyApplication(
            UUID id,
            String committeeId,
            String committeeName,
            String motivation,
            String status,
            Instant appliedAt,
            Instant reviewedAt) {}

    public record ApplicationSummary(
            UUID id,
            String uid,
            String applicantName,
            String applicantEmail,
            String rollNumber,
            Integer year,
            String committeeId,
            String committeeName,
            String motivation,
            String status,
            Instant appliedAt,
            Instant reviewedAt) {}
}
