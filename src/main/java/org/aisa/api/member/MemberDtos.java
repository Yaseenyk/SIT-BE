package org.aisa.api.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class MemberDtos {

    private MemberDtos() {
    }

    public record MemberResponse(
            UUID id,
            String name,
            String role,
            String committeeId,
            /** Resolved from the association, so it can never disagree with the committee. */
            String committeeName,
            String academicYear,
            String linkedinUrl,
            String githubUrl,
            String email,
            String photoUrl,
            int order) {}

    public record MemberRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 160)
            String name,

            @NotBlank(message = "Role is required")
            @Size(max = 120)
            String role,

            /** Null is allowed: a member can exist before being placed on a committee. */
            @Size(max = 64)
            String committeeId,

            @Size(max = 32) String academicYear,
            @Size(max = 500) String linkedinUrl,
            @Size(max = 500) String githubUrl,

            /*
             * Optional, but validated when present. The old form accepted anything and
             * rendered it into a mailto: link, so a typo produced a dead contact button
             * with no indication anything was wrong.
             */
            @Email(message = "That does not look like an email address")
            @Size(max = 255)
            String email,

            String photoUrl,
            String photoPublicId) {}
}
