package org.aisa.api.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.aisa.api.member.MemberDtos.AdminMemberResponse;
import org.aisa.api.member.MemberDtos.MemberRequest;
import org.aisa.api.member.MemberDtos.MemberResponse;
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
@RequestMapping("/api/v1/members")
@Tag(name = "Members", description = "Student office-bearers")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List members, optionally filtered to one committee")
    public List<MemberResponse> list(@RequestParam(required = false) String committeeId) {
        return service.findAll(committeeId);
    }

    /**
     * The roster WITH contact details. Admin-only, by the rule on `/members/admin` in
     * SecurityConfig — the public listing above deliberately carries neither.
     */
    @GetMapping("/admin")
    @Operation(summary = "Every member including phone and email, for the dashboard")
    public List<AdminMemberResponse> listForAdmin() {
        return service.findAllForAdmin();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One member")
    public MemberResponse get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member")
    public MemberResponse create(@Valid @RequestBody MemberRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a member")
    public MemberResponse update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a member and their photo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
