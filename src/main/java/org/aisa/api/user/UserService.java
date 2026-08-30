package org.aisa.api.user;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.aisa.api.common.ConflictException;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.config.AisaProperties;
import org.aisa.api.firestore.Documents;
import org.aisa.api.application.CommitteeApplicationRepository;
import org.aisa.api.media.MediaService;
import org.aisa.api.registration.EventRegistrationRepository;
import org.aisa.api.security.AuthenticatedUser;
import org.aisa.api.security.CurrentUser;
import org.aisa.api.user.UserDtos.MeResponse;
import org.aisa.api.user.UserDtos.ProfileRequest;
import org.aisa.api.user.UserDtos.RegisterRequest;
import org.aisa.api.user.UserDtos.UpdateUserRequest;
import org.aisa.api.user.UserDtos.UserSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Accounts: completing signup, editing a profile, and the admin-only role changes. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final FirebaseAuth firebaseAuth;
    private final EventRegistrationRepository registrations;
    private final CommitteeApplicationRepository applications;
    private final MediaService media;
    private final AisaProperties.Auth config;

    public UserService(
            UserRepository users,
            FirebaseAuth firebaseAuth,
            EventRegistrationRepository registrations,
            CommitteeApplicationRepository applications,
            MediaService media,
            AisaProperties properties) {
        this.users = users;
        this.firebaseAuth = firebaseAuth;
        this.registrations = registrations;
        this.applications = applications;
        this.media = media;
        this.config = properties.auth();
    }

    // ── Signup ───────────────────────────────────────────────────────────────────

    /**
     * Turns a bare Firebase account into an account on this site.
     *
     * <p>The browser creates the Firebase credential first and calls this second, so by
     * the time we get here the caller already holds a verified token. That ordering is
     * what lets Firebase send the verification mail itself — the Admin SDK can generate
     * that link but cannot deliver it, and only a signed-in client can ask Firebase to
     * send one.
     *
     * <p>It also means this is the only place the domain rule can be enforced. Anyone can
     * create a Firebase account directly with the web API key, which is public by design;
     * what they cannot do is get a {@code users} document, and without one they hold
     * {@code ROLE_UNREGISTERED} and every endpoint refuses them.
     *
     * <p>Idempotent on purpose. A retried request after a dropped response must not be a
     * conflict the student cannot get past — it must simply return the account they
     * already have.
     */
    public MeResponse register(RegisterRequest request) {
        AuthenticatedUser caller = CurrentUser.require();

        Optional<AppUser> existing = users.findByUid(caller.uid());
        if (existing.isPresent()) {
            return toMe(caller, existing.get());
        }

        String email = caller.email();
        if (email == null || email.isBlank()) {
            throw new ConflictException("This sign-in method provides no email address.");
        }
        requireAllowedDomain(email, caller.uid());

        AppUser user = new AppUser(caller.uid(), email.toLowerCase(Locale.ROOT), request.name().trim());
        /*
         * STUDENT, always. Not "ADMIN if this is the first account" — that rule would make
         * the very first request to a freshly deployed site an administrative takeover,
         * and this endpoint is reachable by anyone who can reach the internet. The first
         * admin is created by AdminBootstrapper from an environment variable instead.
         */
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);
        user.setRollNumber(Documents.trimmedOrNull(request.rollNumber()));
        user.setYear(request.year());
        users.save(user);

        log.info("Registered student account {}", user.getUid());
        return toMe(caller, user);
    }

    /**
     * Rejects an address outside the institute, and deletes the Firebase account with it.
     *
     * <p>Leaving the credential behind would let someone sign in forever to a site that
     * refuses them every endpoint — an account in a permanently broken state, and a slow
     * leak of junk into the project's user list. Deleting it means the failed signup
     * leaves nothing behind and the address can be tried again properly.
     */
    private void requireAllowedDomain(String email, String uid) {
        List<String> allowed = config.allowedEmailDomains();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        boolean ok = allowed.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .anyMatch(domain::equals);
        if (ok) {
            return;
        }

        try {
            firebaseAuth.deleteUser(uid);
        } catch (FirebaseAuthException ex) {
            // Logged, not thrown: the signup must still be refused. An orphan credential
            // is untidy, not dangerous — it can reach no endpoint without a profile.
            log.warn("Could not delete the rejected signup {}: {}", uid, ex.getMessage());
        }
        throw new ConflictException(
                "Sign up with your institute email address (" + String.join(", ", allowed) + ").");
    }

    // ── The signed-in caller ─────────────────────────────────────────────────────

    public MeResponse me() {
        AuthenticatedUser caller = CurrentUser.require();
        return toMe(caller, caller.profile());
    }

    /**
     * Records the sign-in, and refreshes the mirrored email.
     *
     * <p>Called by the frontend once per session rather than from the filter: writing on
     * every authenticated request would be a Firestore write per API call.
     */
    public MeResponse touchSignIn() {
        AuthenticatedUser caller = CurrentUser.require();
        AppUser user = caller.profile();
        if (user == null) {
            return toMe(caller, null);
        }
        user.setLastLoginAt(Instant.now());
        if (caller.email() != null) {
            user.setEmail(caller.email().toLowerCase(Locale.ROOT));
        }
        users.save(user);
        return toMe(caller, user);
    }

    public MeResponse updateProfile(ProfileRequest request) {
        AuthenticatedUser caller = CurrentUser.require();
        AppUser user = CurrentUser.requireProfile();

        String previousPhoto = user.getPhotoPublicId();
        user.setName(request.name().trim());
        user.setRollNumber(Documents.trimmedOrNull(request.rollNumber()));
        user.setYear(request.year());
        user.setPhotoUrl(Documents.trimmedOrNull(request.photoUrl()));
        user.setPhotoPublicId(Documents.trimmedOrNull(request.photoPublicId()));
        users.save(user);

        // Replacing a photo releases the one it replaced, or Cloudinary accumulates images
        // nothing references and nobody can find.
        if (previousPhoto != null && !previousPhoto.equals(user.getPhotoPublicId())) {
            media.deleteQuietly(previousPhoto);
        }
        return toMe(caller, user);
    }

    // ── Admin ────────────────────────────────────────────────────────────────────

    public List<UserSummary> list() {
        return users.findAll().stream().map(UserService::toSummary).toList();
    }

    /**
     * Changes someone's role or suspends them.
     *
     * <p>Two guards, both about the same failure: an admin locking every admin out of the
     * site. Demoting or suspending the last remaining admin leaves nobody who can undo it,
     * and there is no console to fix it from — it would need a redeploy with the bootstrap
     * variables set. Refusing self-modification also stops the ordinary slip of demoting
     * yourself while tidying the user list.
     */
    public UserSummary update(String uid, UpdateUserRequest request) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.uid().equals(uid)) {
            throw new ConflictException("You cannot change your own role or suspend yourself.");
        }

        AppUser user = users.findByUid(uid)
                .orElseThrow(() -> new NotFoundException("Account", uid));

        UserRole nextRole = request.role() == null ? user.getRole() : UserRole.parse(request.role());
        AccountStatus nextStatus =
                request.status() == null ? user.getStatus() : AccountStatus.parse(request.status());

        boolean losesAdmin = user.isAdmin()
                && (nextRole != UserRole.ADMIN || nextStatus == AccountStatus.SUSPENDED);
        if (losesAdmin && users.countAdmins() <= 1) {
            throw new ConflictException(
                    "This is the only admin account. Promote someone else first.");
        }

        user.setRole(nextRole);
        user.setStatus(nextStatus);
        users.save(user);

        log.info("{} set account {} to role={} status={}",
                caller.uid(), uid, nextRole, nextStatus);
        return toSummary(user);
    }

    /**
     * Removes an account entirely — the profile here and the credential at Firebase.
     *
     * <p>Both, or the person can still sign in and land permanently on the "finish
     * registering" screen.
     */
    public void delete(String uid) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.uid().equals(uid)) {
            throw new ConflictException("You cannot delete your own account.");
        }
        AppUser user = users.findByUid(uid)
                .orElseThrow(() -> new NotFoundException("Account", uid));
        if (user.isAdmin() && users.countAdmins() <= 1) {
            throw new ConflictException("This is the only admin account.");
        }

        /*
         * Everything this person left behind, before the account itself. A registration or
         * application whose uid resolves to nothing is unreadable — the attendance list
         * would show a blank row that no admin could explain or remove.
         */
        int cancelled = registrations.deleteByUid(uid);
        int withdrawn = applications.deleteByUid(uid);

        users.deleteByUid(uid);
        if (user.getPhotoPublicId() != null) {
            media.deleteQuietly(user.getPhotoPublicId());
        }
        try {
            firebaseAuth.deleteUser(uid);
        } catch (FirebaseAuthException ex) {
            log.warn("Deleted the profile for {} but not the Firebase credential: {}",
                    uid, ex.getMessage());
        }
        log.info("{} deleted account {} ({} registration(s), {} application(s) removed)",
                caller.uid(), uid, cancelled, withdrawn);
    }

    /** Used by the bootstrapper; not reachable over HTTP. */
    public Optional<UserRecord> findFirebaseUserByEmail(String email) {
        try {
            return Optional.of(firebaseAuth.getUserByEmail(email));
        } catch (FirebaseAuthException ex) {
            return Optional.empty();
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────────

    private static MeResponse toMe(AuthenticatedUser caller, AppUser user) {
        return new MeResponse(
                caller.uid(),
                caller.email(),
                user != null ? user.getName() : caller.name(),
                user != null ? user.getRole().name() : null,
                state(caller, user),
                caller.emailVerified(),
                user != null ? user.getRollNumber() : null,
                user != null ? user.getYear() : null,
                user != null ? user.getPhotoUrl() : null,
                user != null ? user.getLastLoginAt() : null);
    }

    /**
     * The one field the account UI branches on.
     *
     * <p>Same order as {@code FirebaseAuthenticationFilter.authorityFor}, and it has to
     * stay that way — this string is how the frontend explains the 401 or 403 that the
     * filter's decision would otherwise produce with no reason attached.
     */
    private static String state(AuthenticatedUser caller, AppUser user) {
        if (user == null) {
            return "UNREGISTERED";
        }
        if (user.isSuspended()) {
            return "SUSPENDED";
        }
        if (!caller.emailVerified()) {
            return "UNVERIFIED";
        }
        return "ACTIVE";
    }

    static UserSummary toSummary(AppUser user) {
        return new UserSummary(
                user.getUid(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getRollNumber(),
                user.getYear(),
                user.getPhotoUrl(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
