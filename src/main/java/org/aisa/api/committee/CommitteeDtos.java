package org.aisa.api.committee;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CommitteeDtos {

    private CommitteeDtos() {
    }

    /**
     * What the public site and the dashboard both read.
     *
     * <p>A DTO rather than the entity: serialising {@link Committee} directly would expose
     * the Cloudinary public ids (an admin-only detail) and couple the JSON shape to the
     * column names, so a rename in the schema would silently break the frontend.
     */
    public record CommitteeResponse(
            String id,
            int order,
            String type,
            String name,
            String icon,
            String gradient,
            String sizeLabel,
            String badge,
            String coordLabel,
            String coordinator,
            String coordinatorSub,
            String coordinatorPhoto,
            String coord2Label,
            String coordinator2,
            String coordinator2Photo,
            List<String> responsibilities,
            int memberCount) {}

    public record CommitteeRequest(
            /*
             * Slug rules are enforced here, not left to the admin: the id becomes a URL
             * fragment (#committee-<id>), and a value with a space or a slash produces a
             * link that silently never resolves.
             */
            @NotBlank(message = "Id is required")
            @Size(max = 64, message = "Id must be 64 characters or fewer")
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$",
                    message = "Id must be lowercase letters, numbers and hyphens")
            String id,

            @NotBlank(message = "Name is required")
            @Size(max = 160)
            String name,

            @NotBlank(message = "Type is required")
            @Pattern(regexp = "advisory|executive|functional",
                    message = "Type must be advisory, executive or functional")
            String type,

            @Size(max = 16) String icon,
            @Size(max = 160) String gradient,
            @Size(max = 64) String sizeLabel,
            @Size(max = 64) String badge,
            @Size(max = 96) String coordLabel,
            @Size(max = 160) String coordinator,
            @Size(max = 160) String coordinatorSub,
            String coordinatorPhoto,
            String coordinatorPhotoId,
            @Size(max = 96) String coord2Label,
            @Size(max = 160) String coordinator2,
            String coordinator2Photo,
            String coordinator2PhotoId,
            List<@NotBlank String> responsibilities) {}

    /** Body of {@code PATCH /committees/{id}/order}. */
    public record MoveRequest(
            @NotBlank
            @Pattern(regexp = "up|down", message = "Direction must be up or down")
            @JsonProperty("direction")
            String direction) {}
}
