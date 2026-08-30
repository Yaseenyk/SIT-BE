package org.aisa.api.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
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
        Jwt jwt,
        Admin admin,
        Cloudinary cloudinary,
        Contact contact) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(
            /*
             * No default. A signing key committed to the repository is not a secret, and
             * anyone holding it can mint a valid admin token for the live site.
             */
            @NotBlank String secret,
            String issuer,
            Duration accessTokenTtl) {}

    public record Admin(
            String bootstrapUsername,
            String bootstrapPassword,
            int maxFailedAttempts,
            Duration lockoutDuration) {}

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

    public record Contact(int rateLimitPerHour) {}
}
