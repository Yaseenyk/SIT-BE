package org.aisa.api.message;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aisa.api.message.ContactMessageService.ContactRequest;
import org.aisa.api.message.ContactMessageService.MessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Contact form submissions")
public class ContactMessageController {

    private final ContactMessageService service;

    public ContactMessageController(ContactMessageService service) {
        this.service = service;
    }

    /**
     * Public. Always answers 202 with the same body, whether the message was stored or
     * quietly dropped as spam — a bot that can tell the difference can tune around it.
     */
    @PostMapping
    @Operation(summary = "Send a message from the contact form")
    public ResponseEntity<Map<String, String>> submit(
            @Valid @RequestBody ContactRequest request, HttpServletRequest httpRequest) {
        service.submit(request, httpRequest);
        return ResponseEntity.accepted()
                .body(Map.of("message", "Thanks — we will get back to you soon."));
    }

    @GetMapping
    @Operation(summary = "Read the inbox (admin)")
    public List<MessageResponse> list() {
        return service.findAll();
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a message read or unread (admin)")
    public MessageResponse markRead(@PathVariable UUID id, @RequestBody ReadRequest request) {
        return service.markRead(id, request.read());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a message (admin)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** A body rather than a bare PATCH, so the same endpoint can also mark unread. */
    public record ReadRequest(boolean read) {}
}
