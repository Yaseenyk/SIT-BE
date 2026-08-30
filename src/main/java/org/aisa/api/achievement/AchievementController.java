package org.aisa.api.achievement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.aisa.api.achievement.AchievementService.AchievementRequest;
import org.aisa.api.achievement.AchievementService.AchievementResponse;
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
@RequestMapping("/api/v1/achievements")
@Tag(name = "Achievements", description = "Student wins, publications and selections")
public class AchievementController {

    private final AchievementService service;

    public AchievementController(AchievementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List achievements, newest first")
    public List<AchievementResponse> list(@RequestParam(required = false) String category) {
        return service.findAll(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an achievement")
    public AchievementResponse create(@Valid @RequestBody AchievementRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an achievement")
    public AchievementResponse update(@PathVariable UUID id, @Valid @RequestBody AchievementRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an achievement and its photo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
