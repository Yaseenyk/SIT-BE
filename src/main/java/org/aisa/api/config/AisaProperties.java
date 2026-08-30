package org.aisa.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Every tunable this application has, bound from {@code aisa.*} in application.yml and
 * therefore ultimately from the environment / {@code .env}.
 *
 * <p>Typed and validated rather than scattered {@code @Value} lookups: a missing or
 * malformed setting fails the context at startup with the property path in the message,
 * instead of throwing on the first request that happens to need it.
 */
@Validated
@ConfigurationProperties(prefix = "aisa")
public record AisaProperties(
        Cors cors,
        Auth auth,
        Admin admin,
        Cloudinary cloudinary,
        Contact contact) {

    public record Cors(List<String> allowedOrigins) {}

    /**
     * Who is allowed to hold an account.
     *
     * <p>There is no JWT signing key any more: Firebase issues and signs the tokens, and
     * this API verifies them against Google's public keys. One fewer secret to hold, and
     * one fewer way to leak admin access by committing one.
     */
    public record Auth(
            /*
             * The signup form is on a public site. Without a domain rule, the account list
             * fills with whatever the internet sends it, and an account stops being any
             * evidence that its holder is a student here.
             *
             * An EMPTY list means no restriction, which is a real configuration for a
             * college that uses personal addresses — but it is a decision someone has to
             * make by clearing the variable, not the default.
             */
            List<String> allowedEmailDomains) {}

    public record Admin(
            /*
             * The first admin, created at boot if no admin exists. An email now rather
             * than a username, because Firebase Auth identifies accounts by address.
             */
            String bootstrapEmail,
            String bootstrapPassword,
            String bootstrapName) {}

    public record Cloudinary(
            String cloudName,
            String apiKey,
            String apiSecret,
            String uploadFolder) {

        /** Image endpoints return 503 rather than 500 when this is not configured. */
        public boolean isConfigured() {
            return cloudName != null && !cloudName.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && apiSecret != null && !apiSecret.isBlank();
        }
    }

    public record Contact(
            int rateLimitPerHour,

            /*
             * Salt for the hashed caller IP that rate limiting counts against.
             *
             * This used to borrow the JWT signing key, on the reasoning that there should
             * not be a second secret to configure and forget. Firebase issues the tokens
             * now, so that key is gone and the salt needs a source of its own — it
             * defaults to the service-account JSON, which is server-only, always present
             * in production, and stable across restarts.
             *
             * Stability is the property that matters: a salt regenerated at startup would
             * reset every rate-limit counter, and this runs on a free tier that sleeps
             * after fifteen minutes of quiet. The limit would effectively never apply.
             */
            String hashSalt) {}
}
