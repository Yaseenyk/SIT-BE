package org.aisa.api.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.aisa.api.application.ApplicationDtos.ApplicationSummary;
import org.aisa.api.application.ApplicationDtos.ApplyRequest;
import org.aisa.api.application.ApplicationDtos.MyApplication;
import org.aisa.api.application.ApplicationDtos.ReviewRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Committee applications.
 *
 * <p>The split of paths is the authorisation boundary made visible: {@code /me/…} is the
 * caller's own data and needs only a signed-in account, while {@code /applications} without
 * a prefix is the review queue and falls through to the admin-only rule.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Committee applications")
public class CommitteeApplicationController {

    private final CommitteeApplicationService applications;

    public CommitteeApplicationController(CommitteeApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping("/applications")
    @Operation(summary = "Apply to join a committee")
    public ResponseEntity<MyApplication> apply(@Valid @RequestBody ApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applications.apply(request));
    }

    @GetMapping("/me/applications")
    @Operation(summary = "Your own applications and where they stand")
    public List<MyApplication> mine() {
        return applications.mine();
    }

    @DeleteMapping("/me/applications/{id}")
    @Operation(summary = "Withdraw an application that has not been reviewed yet")
    public ResponseEntity<Void> withdraw(@PathVariable UUID id) {
        applications.withdraw(id);
        return ResponseEntity.noContent().build();
    }

    // ── Admin ────────────────────────────────────────────────────────────────────

    @GetMapping("/applications")
    @Operation(summary = "The review queue, optionally filtered by status")
    public List<ApplicationSummary> list(@RequestParam(required = false) String status) {
        return applications.list(status);
    }

    @PatchMapping("/applications/{id}")
    @Operation(summary = "Accept or reject. Accepting adds the student to the roster")
    public ApplicationSummary review(
            @PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return applications.review(id, request);
    }
}
