package org.aisa.api.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class MemberDtos {

    private MemberDtos() {
    }

    /**
     * What a visitor sees. Carries NO contact details, deliberately.
     *
     * <p>`email` used to be here and it was a latent leak waiting for the first roster
     * import: every member on this site comes from an internal record of students'
     * personal mail addresses and mobile numbers, and this endpoint is public. The roster
     * is public information; how to phone a nineteen-year-old is not.
     */
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
            String photoUrl,
            int order) {}

    /** The same member, with the contact columns, behind the admin rule. */
    public record AdminMemberResponse(
            UUID id,
            String name,
            String role,
            String committeeId,
            String committeeName,
            String academicYear,
            String linkedinUrl,
            String githubUrl,
            String email,
            String phone,
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

            /*
             * Free text rather than a pattern. Indian mobile numbers arrive as
             * "9699363851", "+91 96993 63851" and every spacing in between; a regex here
             * would reject a correct number typed a different way, and the field is only
             * ever read by a person.
             */
            @Size(max = 32) String phone,

            String photoUrl,
            String photoPublicId) {}
}
