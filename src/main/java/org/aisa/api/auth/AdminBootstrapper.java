package org.aisa.api.auth;

import org.aisa.api.config.AisaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the first admin, once, if none exists.
 *
 * <p>This cannot be a SQL migration: the password must be BCrypt-hashed with the
 * application's encoder, and a hash committed to a migration file is a shipped
 * credential. So the hash is computed at boot from an environment variable that never
 * enters the repository.
 *
 * <p>If the variable is unset the application starts normally with no admin and says so
 * in the log. That is the correct behaviour for a redeploy — the admin already exists and
 * the variable has since been cleared — and it means a misconfigured deployment cannot
 * quietly create an account with a guessable password.
 */
@Configuration
public class AdminBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    @Bean
    ApplicationRunner createFirstAdmin(
            AdminUserRepository admins, PasswordEncoder passwordEncoder, AisaProperties properties) {
        return args -> {
            if (admins.count() > 0) {
                return;
            }

            AisaProperties.Admin config = properties.admin();
            String password = config.bootstrapPassword();
            if (password == null || password.isBlank()) {
                log.warn("No admin account exists and ADMIN_BOOTSTRAP_PASSWORD is not set. "
                        + "Set it and restart to create '{}', then unset it.", config.bootstrapUsername());
                return;
            }
            if (password.length() < 10) {
                throw new IllegalStateException(
                        "ADMIN_BOOTSTRAP_PASSWORD must be at least 10 characters.");
            }

            AdminUser admin = new AdminUser(config.bootstrapUsername(), passwordEncoder.encode(password));
            admins.save(admin);
            log.info("Created the first admin account '{}'. Change the password from the "
                    + "dashboard and clear ADMIN_BOOTSTRAP_PASSWORD.", admin.getUsername());
        };
    }
}
