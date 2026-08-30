package org.aisa.api.registration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    /**
     * A row on the student's own list.
     *
     * <p>Carries the event, not the registration: "which events am I signed up for" is the
     * question being asked, and answering it with registration ids would force the client
     * into a second request per row.
     */
    public record MyRegistration(
            UUID eventId,
            String title,
            LocalDate startsOn,
            String dateLabel,
            Instant registeredAt) {}

    /**
     * A row on the admin's attendance list.
     *
     * <p>No uid-free variant: an admin needs to be able to tell two students with the same
     * name apart, and the roll number is not guaranteed to be filled in.
     */
    public record RegistrationSummary(
            String uid,
            String name,
            String email,
            String rollNumber,
            Integer year,
            Instant registeredAt) {}
}
