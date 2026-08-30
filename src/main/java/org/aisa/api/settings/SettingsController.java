package org.aisa.api.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.aisa.api.settings.SettingsService.AdminSettings;
import org.aisa.api.settings.SettingsService.Announcement;
import org.aisa.api.settings.SettingsService.AnnouncementRequest;
import org.aisa.api.settings.SettingsService.PublicSettings;
import org.aisa.api.settings.SettingsService.SettingsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "Contact details, About copy, and the announcement bar")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    /** Public: every page reads this for the footer, contact block and announcement. */
    @GetMapping
    @Operation(summary = "Site settings and the live announcement")
    public PublicSettings get() {
        return service.getPublic();
    }

    /**
     * Admin-only, and on a distinct path rather than the same one with a flag.
     * A "?includePrivate=true" parameter on the public route is one forgotten auth check
     * away from publishing the staff notification address.
     */
    @GetMapping("/admin")
    @Operation(summary = "Settings including admin-only fields")
    public AdminSettings getForAdmin() {
        return service.getForAdmin();
    }

    @PutMapping
    @Operation(summary = "Replace site settings")
    public AdminSettings update(@Valid @RequestBody SettingsRequest request) {
        return service.update(request);
    }

    @PutMapping("/announcement")
    @Operation(summary = "Publish or replace the announcement bar")
    public Announcement setAnnouncement(@Valid @RequestBody AnnouncementRequest request) {
        return service.setAnnouncement(request);
    }

    @DeleteMapping("/announcement")
    @Operation(summary = "Take the announcement bar down")
    public ResponseEntity<Void> clearAnnouncement() {
        service.clearAnnouncement();
        return ResponseEntity.noContent().build();
    }
}
