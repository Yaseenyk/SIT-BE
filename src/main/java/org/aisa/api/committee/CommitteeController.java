package org.aisa.api.committee;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aisa.api.committee.CommitteeDtos.CommitteeRequest;
import org.aisa.api.committee.CommitteeDtos.CommitteeResponse;
import org.aisa.api.committee.CommitteeDtos.MoveRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/committees")
@Tag(name = "Committees", description = "The association structure")
public class CommitteeController {

    private final CommitteeService service;

    public CommitteeController(CommitteeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List committees in display order")
    public List<CommitteeResponse> list(@RequestParam(required = false) String type) {
        return service.findAll(type);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One committee by slug")
    public CommitteeResponse get(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a committee")
    public CommitteeResponse create(@Valid @RequestBody CommitteeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a committee")
    public CommitteeResponse update(@PathVariable String id, @Valid @RequestBody CommitteeRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/order")
    @Operation(summary = "Move a committee up or down; returns the reordered list")
    public List<CommitteeResponse> move(@PathVariable String id, @Valid @RequestBody MoveRequest request) {
        return service.move(id, request.direction());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a committee; its members become unassigned")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
