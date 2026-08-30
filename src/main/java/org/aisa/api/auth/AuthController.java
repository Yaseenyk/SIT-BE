package org.aisa.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.aisa.api.auth.AuthDtos.ChangePasswordRequest;
import org.aisa.api.auth.AuthDtos.ChangeUsernameRequest;
import org.aisa.api.auth.AuthDtos.LoginRequest;
import org.aisa.api.auth.AuthDtos.LoginResponse;
import org.aisa.api.auth.AuthDtos.MeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Admin sign-in and credential management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in and receive an access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Who the current token belongs to")
    public MeResponse me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the signed-in admin password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-username")
    @Operation(summary = "Change the signed-in admin username; returns a reissued token")
    public LoginResponse changeUsername(@Valid @RequestBody ChangeUsernameRequest request) {
        return authService.changeUsername(request);
    }

    /*
     * There is no /logout. The token is stateless and short-lived, so signing out is the
     * client discarding it; an endpoint here would imply a server-side revocation that
     * does not exist. Rotate JWT_SECRET to invalidate every issued token at once.
     */
}
