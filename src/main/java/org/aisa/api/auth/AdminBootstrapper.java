package org.aisa.api.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.aisa.api.config.AisaProperties;
import org.aisa.api.user.AccountStatus;
import org.aisa.api.user.AppUser;
import org.aisa.api.user.UserRepository;
import org.aisa.api.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the first admin, once, if none exists.
 *
 * <p>Signing up can only ever produce a student (see {@code UserService.register}), so
 * without this there would be no way to get the first admin onto a fresh deployment. It
 * has to be an environment variable rather than a seeded document because a password
 * committed to the repository is a shipped credential.
 *
 * <h2>Why an unset variable is a warning and not a failure</h2>
 *
 * <p>Because this application also serves the public site. Refusing to start would take
 * down the whole thing — committees, events, contact form — over a variable that is
 * <em>expected</em> to be absent on every redeploy after the first. A loud warning leaves
 * visitors unaffected and tells whoever reads the log exactly what to set.
 */
@Configuration
public class AdminBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    @Bean
    ApplicationRunner createFirstAdmin(
            UserRepository users, FirebaseAuth firebaseAuth, AisaProperties properties) {
        return args -> {
            if (users.countAdmins() > 0) {
                return;
            }

            AisaProperties.Admin config = properties.admin();
            String email = config.bootstrapEmail();
            String password = config.bootstrapPassword();

            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.warn("No admin account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD and restart "
                        + "to create the first one, then clear ADMIN_PASSWORD. The public site is "
                        + "unaffected; only the dashboard is unreachable until then.");
                return;
            }
            if (password.length() < 10) {
                throw new IllegalStateException("ADMIN_PASSWORD must be at least 10 characters.");
            }

            String name = config.bootstrapName() == null || config.bootstrapName().isBlank()
                    ? "AISA Admin"
                    : config.bootstrapName();

            UserRecord record = findOrCreate(firebaseAuth, email, password, name);
            if (record == null) {
                return;
            }

            /*
             * Promote whatever profile exists rather than overwriting it. On a redeploy
             * where the admin has since filled in their name and photo, replacing the
             * document would silently throw that away.
             */
            AppUser admin = users.findByUid(record.getUid())
                    .orElseGet(() -> new AppUser(record.getUid(), email.toLowerCase(), name));
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(AccountStatus.ACTIVE);
            users.save(admin);

            log.info("Created the first admin account for {}. Sign in, change the password, "
                    + "and clear ADMIN_PASSWORD from the environment.", email);
        };
    }

    /**
     * Reuses the Firebase account for this address if there is one.
     *
     * <p>The realistic case is an admin who signed up through the public form first and is
     * now being made an admin by setting the variables. Creating would fail with
     * "email already exists" and leave them a student for ever.
     *
     * <p>The existing account's password is deliberately NOT reset here. Overwriting it
     * would mean anyone who could set an environment variable could take over an account,
     * and it would silently change a password its owner is still using.
     */
    private static UserRecord findOrCreate(
            FirebaseAuth firebaseAuth, String email, String password, String name) {
        try {
            UserRecord existing = firebaseAuth.getUserByEmail(email);
            log.info("A Firebase account already exists for {}; promoting it rather than "
                    + "creating one. ADMIN_PASSWORD was ignored.", email);
            return existing;
        } catch (FirebaseAuthException notFound) {
            // Fall through: no account yet, which is the first-deployment case.
        }

        try {
            return firebaseAuth.createUser(new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(name)
                    // Marked verified because this address came from the deployment's own
                    // environment, not from a form anyone on the internet can submit.
                    .setEmailVerified(true));
        } catch (FirebaseAuthException ex) {
            log.error("Could not create the first admin account for {}: {}", email, ex.getMessage());
            return null;
        }
    }
}
