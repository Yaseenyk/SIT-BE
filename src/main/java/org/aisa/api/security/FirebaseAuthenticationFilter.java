package org.aisa.api.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.aisa.api.user.AppUser;
import org.aisa.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <firebase-id-token>} and populates the security
 * context.
 *
 * <p>A missing or invalid token is not rejected here — the request simply stays anonymous
 * and {@link org.aisa.api.config.SecurityConfig} decides whether that is allowed. Public
 * GETs must keep working for visitors who have no account at all.
 *
 * <h2>The four states a valid token can be in</h2>
 *
 * <p>A signature that verifies is not the same as permission to act, and collapsing the
 * difference into "authenticated or not" is what makes the resulting UX unexplainable —
 * the caller gets a 401 with no way to know which of these they are in. So each gets its
 * own authority, and each has a screen behind it:
 *
 * <ul>
 *   <li>{@code ROLE_UNREGISTERED} — Firebase knows them, this site does not. Reachable if
 *       signup was interrupted after the Firebase account was created but before
 *       {@code POST /auth/register} completed. They are offered registration again.
 *   <li>{@code ROLE_UNVERIFIED} — registered, but the address has not been confirmed.
 *       Signup is open to the public, so an unconfirmed address proves nothing about who
 *       owns it. They are offered a resend.
 *   <li>{@code ROLE_SUSPENDED} — an admin has disabled the account.
 *   <li>{@code ROLE_STUDENT} / {@code ROLE_ADMIN} — the normal case.
 * </ul>
 *
 * <h2>Why the role is read from Firestore and not from a custom claim</h2>
 *
 * <p>Firebase custom claims travel inside the token, which makes them free to check — but
 * they are only refreshed when the client fetches a new token, up to an hour later.
 * Revoking an admin would leave that person an admin for the rest of the hour. One
 * document read per authenticated request buys revocation that takes effect on the very
 * next request, and the authenticated traffic here is a few thousand requests a day at the
 * absolute most.
 */
@Component
@Order(1)
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;
    private final UserRepository users;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth, UserRepository users) {
        this.firebaseAuth = firebaseAuth;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        verify(header.substring(PREFIX.length()).trim()).ifPresent(token -> {
            Optional<AppUser> profile = users.findByUid(token.getUid());

            AuthenticatedUser principal = new AuthenticatedUser(
                    token.getUid(),
                    token.getEmail(),
                    // Firebase leaves the display name null for an account the Admin SDK
                    // created without one, so the profile is then the only source for it.
                    token.getName() != null && !token.getName().isBlank()
                            ? token.getName()
                            : profile.map(AppUser::getName).orElse(null),
                    isEmailVerified(token),
                    profile.orElse(null));

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(authorityFor(principal))));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });

        chain.doFilter(request, response);
    }

    /**
     * Whether the address really is confirmed — not merely whether this token says so.
     *
     * <p>The claim inside the token is a SNAPSHOT from when it was issued, and confirming
     * an address happens somewhere else entirely: in a mail client, often on another
     * device. The browser's token keeps saying {@code false} until it is refreshed, which
     * can be the best part of an hour later.
     *
     * <p>That made the most important flow on the site unrecoverable. A student clicked the
     * link in their email, came back, and every action still returned 403 — with the client
     * showing them as verified, because the client HAD re-read the user record. Forcing a
     * token refresh from the browser is the documented fix and it is what the client does,
     * but a client that fails to do it must not leave an account stuck: whether someone may
     * act is this server's decision, so this server checks.
     *
     * <p>The extra call happens only when the token claims NOT verified, so it costs
     * nothing for the overwhelming majority of requests and stops entirely as soon as the
     * token catches up.
     */
    private boolean isEmailVerified(FirebaseToken token) {
        if (token.isEmailVerified()) {
            return true;
        }
        try {
            return firebaseAuth.getUser(token.getUid()).isEmailVerified();
        } catch (FirebaseAuthException ex) {
            // Trust the token rather than failing the request: the worst case is that a
            // just-verified account waits for its next token refresh, which is where this
            // started, and not an outage.
            log.debug("Could not re-check verification for {}: {}", token.getUid(), ex.getMessage());
            return false;
        }
    }

    private static String authorityFor(AuthenticatedUser user) {
        if (!user.isRegistered()) {
            return "ROLE_UNREGISTERED";
        }
        if (user.profile().isSuspended()) {
            return "ROLE_SUSPENDED";
        }
        if (!user.emailVerified()) {
            return "ROLE_UNVERIFIED";
        }
        return user.profile().getRole().authority();
    }

    /**
     * Verifies signature, issuer, audience and expiry against Google's public keys.
     *
     * <p>Returns empty rather than throwing: an expired token is the normal state of a
     * browser tab left open overnight, not an exceptional condition, and the filter must
     * simply leave the request anonymous.
     *
     * <p>{@code checkRevoked} is left off — it would cost a network round trip to Google
     * on every single request. Revocation is already covered by the Firestore read above:
     * a suspended or deleted account loses access on its next request either way.
     */
    private Optional<FirebaseToken> verify(String idToken) {
        if (idToken.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(firebaseAuth.verifyIdToken(idToken));
        } catch (FirebaseAuthException | IllegalArgumentException ex) {
            log.debug("Rejected an ID token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Health probes and the API docs need no identity; skip the token work entirely. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health")
                || path.startsWith("/docs")
                || path.startsWith("/v3/api-docs");
    }
}
