package org.aisa.api.registration;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.aisa.api.common.ConflictException;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.event.Event;
import org.aisa.api.event.EventRepository;
import org.aisa.api.registration.RegistrationDtos.MyRegistration;
import org.aisa.api.registration.RegistrationDtos.RegistrationSummary;
import org.aisa.api.security.CurrentUser;
import org.aisa.api.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Signing up for events.
 *
 * <p>Every method here takes the student from the security context, never from the
 * request. There is deliberately no endpoint that registers a named person: if the uid
 * came from the body, one student could sign another up, or cancel their place.
 */
@Service
public class EventRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(EventRegistrationService.class);

    private final EventRegistrationRepository registrations;
    private final EventRepository events;
    private final Clock clock;

    public EventRegistrationService(
            EventRegistrationRepository registrations, EventRepository events, Clock clock) {
        this.registrations = registrations;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Registers the caller, idempotently.
     *
     * <p>Registering twice returns the existing place rather than a conflict. The student
     * pressing the button again means "am I signed up?", and answering that with an error
     * would teach them that they are not.
     */
    public MyRegistration register(UUID eventId) {
        AppUser student = CurrentUser.requireProfile();
        Event event = events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event", eventId));

        // A past event is a record, not an invitation. Without this, a student could sign
        // up for something that finished last year and appear on its attendance list.
        if (event.isPast(LocalDate.now(clock))) {
            throw new ConflictException("This event has already taken place.");
        }

        EventRegistration registration = registrations.find(eventId, student.getUid())
                .orElseGet(() -> new EventRegistration(
                        eventId, student.getUid(), student.getName(), student.getEmail()));

        // Refreshed on every call, so a student who fills in their roll number after
        // registering is not stuck on the attendance list without one.
        registration.setName(student.getName());
        registration.setEmail(student.getEmail());
        registration.setRollNumber(student.getRollNumber());
        registration.setYear(student.getYear());
        registrations.save(registration);

        log.info("{} registered for event {}", student.getUid(), eventId);
        return toMine(registration, event);
    }

    public void cancel(UUID eventId) {
        String uid = CurrentUser.requireUid();
        // No existence check first: deleting something already gone is the outcome the
        // caller wanted, and a 404 here would only be confusing.
        registrations.delete(eventId, uid);
        log.info("{} cancelled their registration for event {}", uid, eventId);
    }

    /** The caller's own registrations, upcoming first. */
    public List<MyRegistration> mine() {
        String uid = CurrentUser.requireUid();
        return registrations.findByUid(uid).stream()
                .map(registration -> events.findById(registration.getEventId())
                        .map(event -> toMine(registration, event))
                        .orElse(null))
                // An event deleted between the two reads. Dropping the row is right:
                // there is nothing to show, and it is about to be cleaned up anyway.
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> b.startsOn().compareTo(a.startsOn()))
                .toList();
    }

    // ── Admin ────────────────────────────────────────────────────────────────────

    public List<RegistrationSummary> forEvent(UUID eventId) {
        if (events.findById(eventId).isEmpty()) {
            throw new NotFoundException("Event", eventId);
        }
        return registrations.findByEvent(eventId).stream()
                .map(registration -> new RegistrationSummary(
                        registration.getUid(),
                        registration.getName(),
                        registration.getEmail(),
                        registration.getRollNumber(),
                        registration.getYear(),
                        registration.getCreatedAt()))
                .toList();
    }

    private static MyRegistration toMine(EventRegistration registration, Event event) {
        return new MyRegistration(
                event.getId(),
                event.getTitle(),
                event.getStartsOn(),
                event.displayDate(),
                registration.getCreatedAt());
    }
}
