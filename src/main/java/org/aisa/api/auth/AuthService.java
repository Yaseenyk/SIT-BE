package org.aisa.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.aisa.api.auth.AuthDtos.ChangePasswordRequest;
import org.aisa.api.auth.AuthDtos.ChangeUsernameRequest;
import org.aisa.api.auth.AuthDtos.LoginRequest;
import org.aisa.api.auth.AuthDtos.LoginResponse;
import org.aisa.api.auth.AuthDtos.MeResponse;
import org.aisa.api.common.ConflictException;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.common.RateLimitedException;
import org.aisa.api.config.AisaProperties;
import org.aisa.api.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AisaProperties.Admin config;

    public AuthService(
            AdminUserRepository admins,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AisaProperties properties) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.config = properties.admin();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Instant now = Instant.now();
        AdminUser admin = admins.findByUsernameIgnoreCase(request.username().trim())
                /*
                 * Same exception as a wrong password, and no early return: an attacker
                 * who can tell "no such user" from "wrong password" can enumerate
                 * usernames from the login form alone.
                 */
                .orElseThrow(() -> new BadCredentialsException("Unknown admin"));

        if (admin.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, admin.getLockedUntil()).toMinutes());
            throw new RateLimitedException(
                    "Too many failed attempts. Try again in " + minutes + " minute(s).");
        }

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            admin.recordFailure(config.maxFailedAttempts(), config.lockoutDuration(), now);
            admins.save(admin);
            log.warn("Failed admin login for '{}'", admin.getUsername());
            throw new BadCredentialsException("Wrong password");
        }

        admin.recordSuccess(now);
        admins.save(admin);
        log.info("Admin '{}' signed in", admin.getUsername());

        return new LoginResponse(
                jwtService.issue(admin.getId(), admin.getUsername()),
                "Bearer",
                jwtService.ttl().toSeconds(),
                admin.getUsername());
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AdminUser admin = requireCurrentAdmin();
        return new MeResponse(admin.getUsername(), admin.getLastLoginAt());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AdminUser admin = requireCurrentAdmin();
        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        admins.save(admin);
        log.info("Admin '{}' changed their password", admin.getUsername());
    }

    /**
     * Renaming requires the password even though the caller already holds a valid token.
     * A token left behind on a shared lab machine should not be enough to change the
     * login name and lock the real admin out.
     */
    @Transactional
    public LoginResponse changeUsername(ChangeUsernameRequest request) {
        AdminUser admin = requireCurrentAdmin();
        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new BadCredentialsException("Password is incorrect");
        }
        String next = request.newUsername().trim();
        if (!next.equalsIgnoreCase(admin.getUsername()) && admins.existsByUsernameIgnoreCase(next)) {
            throw new ConflictException("That username is already taken");
        }
        admin.setUsername(next);
        admins.save(admin);

        // The old token carries the old username in its claims. Reissue so the dashboard
        // does not keep greeting them by a name that no longer exists.
        return new LoginResponse(
                jwtService.issue(admin.getId(), next),
                "Bearer",
                jwtService.ttl().toSeconds(),
                next);
    }

    private AdminUser requireCurrentAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof JwtService.AuthenticatedAdmin authenticated)) {
            throw new BadCredentialsException("Not signed in");
        }
        UUID id = authenticated.id();
        return admins.findById(id)
                // A token that outlives the row it names: valid signature, deleted admin.
                .orElseThrow(() -> new NotFoundException("Admin account", id));
    }
}
