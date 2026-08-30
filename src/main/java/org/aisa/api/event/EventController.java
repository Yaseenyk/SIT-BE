package org.aisa.api.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.aisa.api.event.EventDtos.EventRequest;
import org.aisa.api.event.EventDtos.EventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Workshops, hackathons, lectures and competitions")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List events; status=upcoming|past, omit for all")
    public List<EventResponse> list(@RequestParam(required = false) String status) {
        return service.findAll(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One event")
    public EventResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an event")
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an event")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an event and its banner")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
