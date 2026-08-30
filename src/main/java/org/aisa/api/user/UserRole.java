package org.aisa.api.user;

import java.util.Locale;

/**
 * What an account may do.
 *
 * <p>Two roles, because the site has two kinds of person: students, who register for
 * events and apply to committees, and admins, who run the site.
 *
 * <p>There is deliberately no way to acquire {@link #ADMIN} by signing up. A public
 * signup form that can grant administrative access is the same bug as an unauthenticated
 * write endpoint, only easier to find. Promotion is an admin-only write on an existing
 * account — see {@code UserService.changeRole}.
 */
public enum UserRole {
    STUDENT,
    ADMIN;

    /** Spring Security's convention: authorities are the role name with a ROLE_ prefix. */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * Reads a stored value, defaulting to the least privileged role.
     *
     * <p>Defaulting rather than throwing matters: a document with a missing, misspelled or
     * hand-edited {@code role} field must not become an admin, and it must not take the
     * whole user listing down either. STUDENT is the safe answer to both.
     */
    public static UserRole parse(String value) {
        if (value == null) {
            return STUDENT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return STUDENT;
        }
    }
}
