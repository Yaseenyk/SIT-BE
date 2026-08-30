package org.aisa.api.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventResponse(
            UUID id,
            String title,
            LocalDate startsOn,
            LocalDate endsOn,
            /** Pre-rendered so the client never re-implements the date formatting. */
            String dateLabel,
            /** "upcoming" or "past" — computed server-side against one clock. */
            String status,
            String tag,
            String emoji,
            String description,
            String linkUrl,
            String bannerUrl) {}

    public record EventRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200)
            String title,

            @NotNull(message = "A start date is required")
            LocalDate startsOn,

            /** Optional; validated against startsOn in the service, not here. */
            LocalDate endsOn,

            @Size(max = 120) String dateLabel,
            @Size(max = 64) String tag,
            @Size(max = 16) String emoji,
            String description,
            @Size(max = 500) String linkUrl,
            String bannerUrl,
            String bannerPublicId) {}
}
