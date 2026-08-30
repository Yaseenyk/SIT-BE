package org.aisa.api.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aisa.api.user.UserDtos.MeResponse;
import org.aisa.api.user.UserDtos.ProfileRequest;
import org.aisa.api.user.UserDtos.RegisterRequest;
import org.aisa.api.user.UserDtos.UpdateUserRequest;
import org.aisa.api.user.UserDtos.UserSummary;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts.
 *
 * <p>There is no login or password endpoint here, and that absence is the design: the
 * browser talks to Firebase for credentials and to this API for everything else. Nothing
 * in this file can see a password, so nothing in this file can leak one.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounts")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Complete signup for a caller who already holds a Firebase account")
    public ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.register(request));
    }

    @GetMapping("/auth/me")
    @Operation(summary = "The signed-in caller, including why they may be unable to act")
    public MeResponse me() {
        return users.me();
    }

    @PostMapping("/auth/session")
    @Operation(summary = "Record a sign-in. Called once per session, not per request")
    public MeResponse session() {
        return users.touchSignIn();
    }

    @PutMapping("/auth/profile")
    @Operation(summary = "Update your own name, year, roll number and photo")
    public MeResponse updateProfile(@Valid @RequestBody ProfileRequest request) {
        return users.updateProfile(request);
    }

    // ── Admin ────────────────────────────────────────────────────────────────────

    @GetMapping("/admin/users")
    @Operation(summary = "Every account")
    public List<UserSummary> list() {
        return users.list();
    }

    @PatchMapping("/admin/users/{uid}")
    @Operation(summary = "Change an account's role, or suspend it")
    public UserSummary update(@PathVariable String uid, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(uid, request);
    }

    @DeleteMapping("/admin/users/{uid}")
    @Operation(summary = "Delete an account and its Firebase credential")
    public ResponseEntity<Void> delete(@PathVariable String uid) {
        users.delete(uid);
        return ResponseEntity.noContent().build();
    }
}
