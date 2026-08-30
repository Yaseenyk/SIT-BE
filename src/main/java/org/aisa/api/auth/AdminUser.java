package org.aisa.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aisa.api.common.BaseEntity;

/**
 * An administrator of the site.
 *
 * <p>The old site kept a Firebase Auth user plus a username stored in a publicly readable
 * Firestore document, so the login name was visible to anyone. Both now live in a table
 * only this API can reach.
 *
 * <p>{@code failedAttempts} and {@code lockedUntil} are the server-side replacement for
 * the browser-side attempt counter and arithmetic captcha the single-file version used:
 * that counter reset on reload, so it stopped nobody who was actually attacking it.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminUser extends BaseEntity {

    private UUID id;

    private String username;

    private String passwordHash;

    private int failedAttempts;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    public AdminUser(String username, String passwordHash) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailure(int maxAttempts, Duration lockoutDuration, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockoutDuration);
            // Reset here rather than on unlock: without it the account re-locks on the
            // very next wrong password instead of allowing a fresh set of attempts.
            failedAttempts = 0;
        }
    }

    public void recordSuccess(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }
}
