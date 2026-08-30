package org.aisa.api.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Accepts a platform-style {@code postgres://user:pass@host:port/db} URL and rewrites it
 * into the JDBC form plus separate credentials.
 *
 * <p>Every managed Postgres — Render, Heroku, Railway, Supabase, Neon — hands out that
 * shape, and the JDBC driver rejects it outright with {@code 'url' must start with "jdbc"}.
 * Render's own blueprint wires {@code fromDatabase.connectionString} straight into
 * {@code DATABASE_URL}, so without this the documented workaround is for a human to pick
 * the string apart by hand and set three variables — a step performed once, under deploy
 * pressure, with no feedback until the container crash-loops on an error that names none
 * of this.
 *
 * <p><b>Why this is called from {@code main} rather than being an
 * {@code EnvironmentPostProcessor}.</b> It was one first. Registering it needs
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports},
 * and in this project's repackaged jar that file lands at the archive ROOT rather than
 * under {@code BOOT-INF/classes/}, where the loader looks — so the processor silently
 * never ran and the app still died on the raw URL. A static call from {@code main} has no
 * registration to get wrong.
 *
 * <p>System properties are used because they outrank environment variables in Spring's
 * property source order, so the converted value wins over the {@code ${DATABASE_URL}}
 * placeholder in {@code application.yml}.
 */
public final class DatabaseUrl {

    private DatabaseUrl() {
    }

    /**
     * Reads {@code DATABASE_URL} from the environment and, if it is a platform-style URL,
     * sets the {@code spring.datasource.*} system properties from it.
     *
     * <p>Does nothing for a {@code jdbc:} URL, so local development and the Testcontainers
     * suite are untouched.
     */
    public static void applyFromEnvironment(Properties target) {
        String raw = System.getenv("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }

        Parsed parsed = parse(raw.trim());
        if (parsed == null) {
            return;
        }

        target.setProperty("spring.datasource.url", parsed.jdbcUrl());
        /*
         * Only set credentials actually present in the URL. A URL without a userinfo
         * section is legitimate — the platform may supply DATABASE_USERNAME separately —
         * and clobbering those would break a working configuration.
         */
        if (parsed.username() != null) {
            target.setProperty("spring.datasource.username", parsed.username());
        }
        if (parsed.password() != null) {
            target.setProperty("spring.datasource.password", parsed.password());
        }
    }

    /** @return the converted parts, or null if this is not a URL we should rewrite */
    static Parsed parse(String value) {
        if (value.startsWith("jdbc:")) {
            return null; // already usable
        }
        if (!value.startsWith("postgres://") && !value.startsWith("postgresql://")) {
            return null; // not ours to interpret
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException ex) {
            // Leave it alone and let the driver produce its own error, rather than
            // masking a malformed value with a half-converted one.
            return null;
        }

        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath() == null ? "" : uri.getPath();

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(host).append(':').append(port).append(path);
        /*
         * The query string carries real options — Supabase and Neon both require
         * `sslmode=require`, and dropping it turns a working URL into a connection
         * refused, which looks like a firewall problem. Preserve it verbatim.
         */
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbc.append('?').append(uri.getQuery());
        }

        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int separator = userInfo.indexOf(':');
            if (separator >= 0) {
                username = decode(userInfo.substring(0, separator));
                password = decode(userInfo.substring(separator + 1));
            } else {
                username = decode(userInfo);
            }
        }

        return new Parsed(jdbc.toString(), username, password);
    }

    /**
     * Generated passwords routinely contain characters that must be percent-encoded in a
     * URL. Handing the encoded form to the driver authenticates with the wrong password.
     */
    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    record Parsed(String jdbcUrl, String username, String password) {}
}
