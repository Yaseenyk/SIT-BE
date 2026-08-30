package org.aisa.api.event;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.event.EventDtos.EventRequest;
import org.aisa.api.event.EventDtos.EventResponse;
import org.aisa.api.media.MediaService;
import org.aisa.api.registration.EventRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final MediaService media;
    /**
     * Injected rather than calling {@code LocalDate.now()} inline, so a test can decide
     * what "today" is. The upcoming/past split is the whole behaviour of this service;
     * testing it against the real wall clock would mean tests that pass until the seeded
     * dates fall into the past.
     */
    private final Clock clock;

    public EventService(
            EventRepository events,
            EventRegistrationRepository registrations,
            MediaService media,
            Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.media = media;
        this.clock = clock;
    }

    public List<EventResponse> findAll(String status) {
        LocalDate today = LocalDate.now(clock);
        List<Event> found = switch (status == null ? "" : status.toLowerCase()) {
            case "upcoming" -> events.findUpcoming(today);
            case "past" -> events.findPast(today);
            default -> events.findAllNewestFirst();
        };
        return found.stream().map(e -> toResponse(e, today)).toList();
    }

    public EventResponse findById(UUID id) {
        return toResponse(require(id), LocalDate.now(clock));
    }

    public EventResponse create(EventRequest request) {
        validateDates(request);
        Event event = new Event(request.title().trim(), request.startsOn());
        apply(event, request);
        return toResponse(events.save(event), LocalDate.now(clock));
    }

    public EventResponse update(UUID id, EventRequest request) {
        validateDates(request);
        Event event = require(id);
        if (event.getBannerPublicId() != null
                && !event.getBannerPublicId().equals(request.bannerPublicId())) {
            media.deleteQuietly(event.getBannerPublicId());
        }
        event.setTitle(request.title().trim());
        event.setStartsOn(request.startsOn());
        apply(event, request);
        return toResponse(events.save(event), LocalDate.now(clock));
    }

    public void delete(UUID id) {
        Event event = require(id);
        media.deleteQuietly(event.getBannerPublicId());

        // Firestore has no cascade, so this IS the cascade. Registrations are deleted
        // BEFORE the event, so a failure part-way leaves rows pointing at an event that
        // still exists rather than orphans nothing can resolve.
        int cancelled = registrations.deleteByEvent(id);
        events.deleteById(id);

        if (cancelled > 0) {
            log.info("Deleted event {} and {} registration(s).", id, cancelled);
        }
    }

    private Event require(UUID id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
    }

    /**
     * Mirrors the {@code event_dates_ordered} check constraint.
     *
     * <p>The constraint is the guarantee; this is the readable message. Without it the
     * admin gets a raw DataIntegrityViolationException surfaced as a 500.
     */
    private void validateDates(EventRequest request) {
        if (request.endsOn() != null && request.endsOn().isBefore(request.startsOn())) {
            throw new IllegalArgumentException("The end date cannot be before the start date");
        }
    }

    private void apply(Event event, EventRequest request) {
        event.setEndsOn(request.endsOn());
        event.setDateLabel(blankToNull(request.dateLabel()));
        event.setTag(blankToNull(request.tag()));
        event.setEmoji(blankToNull(request.emoji()));
        event.setDescription(blankToNull(request.description()));
        event.setLinkUrl(blankToNull(request.linkUrl()));
        event.setBannerUrl(blankToNull(request.bannerUrl()));
        event.setBannerPublicId(blankToNull(request.bannerPublicId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static EventResponse toResponse(Event e, LocalDate today) {
        return new EventResponse(
                e.getId(),
                e.getTitle(),
                e.getStartsOn(),
                e.getEndsOn(),
                e.displayDate(),
                e.isPast(today) ? "past" : "upcoming",
                e.getTag(),
                e.getEmoji(),
                e.getDescription(),
                e.getLinkUrl(),
                e.getBannerUrl());
    }
}
