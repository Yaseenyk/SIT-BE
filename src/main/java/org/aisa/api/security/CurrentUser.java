package org.aisa.api.security;

import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who is making this request.
 *
 * <p>Services need the caller's uid constantly — to register them for an event, to save
 * their profile, to stamp an application. Reading it from a controller parameter instead
 * would mean the uid travels as an argument that a caller could pass wrongly; taking it
 * from the security context means it can only ever be the identity the filter verified.
 *
 * <p>Nothing here decides permissions. {@link org.aisa.api.config.SecurityConfig} has
 * already refused the request if the caller lacked the authority for the endpoint.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * The caller, or a 401.
     *
     * <p>Reaching the else branch means an endpoint is mapped in SecurityConfig as
     * permitAll but its service assumes a caller — so it throws rather than returning null
     * for someone to dereference three frames later.
     */
    public static AuthenticatedUser require() {
        return find().orElseThrow(() -> new BadCredentialsException("Not signed in"));
    }

    /** The caller's Firestore profile, or a 401 when they have never registered. */
    public static org.aisa.api.user.AppUser requireProfile() {
        AuthenticatedUser user = require();
        if (!user.isRegistered()) {
            throw new BadCredentialsException("This account has not completed registration");
        }
        return user.profile();
    }

    public static String requireUid() {
        return require().uid();
    }
}
