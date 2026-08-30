package org.aisa.api.security;

import org.aisa.api.user.AppUser;
import org.aisa.api.user.UserRole;

/**
 * The verified identity behind a request.
 *
 * <p>{@code uid}, {@code email} and {@code emailVerified} come from the Firebase ID token
 * and are therefore signed by Google. {@code role} and {@code status} come from this
 * application's own {@code users} document and are therefore ours — which is the split
 * that matters: Firebase says <em>who</em> the caller is, this API says <em>what they may
 * do</em>. A token cannot carry a role it was not granted here.
 *
 * @param profile the {@code users} document, or null when the caller holds a valid
 *                Firebase token but has never completed registration
 */
public record AuthenticatedUser(
        String uid,
        String email,
        String name,
        boolean emailVerified,
        AppUser profile) {

    public boolean isRegistered() {
        return profile != null;
    }

    public UserRole role() {
        return profile == null ? null : profile.getRole();
    }

    public boolean isAdmin() {
        return profile != null && profile.isAdmin();
    }
}
