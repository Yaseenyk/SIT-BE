package org.aisa.api.registration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.aisa.api.registration.RegistrationDtos.MyRegistration;
import org.aisa.api.registration.RegistrationDtos.RegistrationSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event sign-ups.
 *
 * <p>The student-facing routes are {@code /events/{id}/registration} — singular, and with
 * no id of their own. There is only ever one registration for a given student and event,
 * and its identity is entirely determined by the caller's token plus the path, so exposing
 * an id would add something to tamper with and nothing to use it for.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Event registration")
public class EventRegistrationController {

    private final EventRegistrationService registrations;

    public EventRegistrationController(EventRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping("/events/{eventId}/registration")
    @Operation(summary = "Register yourself for an event")
    public MyRegistration register(@PathVariable UUID eventId) {
        return registrations.register(eventId);
    }

    @DeleteMapping("/events/{eventId}/registration")
    @Operation(summary = "Cancel your own registration")
    public ResponseEntity<Void> cancel(@PathVariable UUID eventId) {
        registrations.cancel(eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/registrations")
    @Operation(summary = "The events you are signed up for")
    public List<MyRegistration> mine() {
        return registrations.mine();
    }

    @GetMapping("/events/{eventId}/registrations")
    @Operation(summary = "Everyone signed up for an event")
    public List<RegistrationSummary> forEvent(@PathVariable UUID eventId) {
        return registrations.forEvent(eventId);
    }
}
