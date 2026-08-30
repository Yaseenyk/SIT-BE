package org.aisa.api.user;

import java.util.Locale;

/**
 * Whether an account may be used.
 *
 * <p>Suspension is kept here rather than using Firebase Auth's own {@code disabled} flag
 * as the source of truth. The account can then be suspended and restored from the
 * dashboard in one write against data this API owns, and the check is visible in the same
 * place as every other authorisation decision instead of living in another system's
 * console.
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED;

    public static AccountStatus parse(String value) {
        if (value == null) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
