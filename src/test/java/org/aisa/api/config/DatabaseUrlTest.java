package org.aisa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.aisa.api.config.DatabaseUrl.Parsed;
import org.junit.jupiter.api.Test;

/**
 * The platform URL conversion.
 *
 * <p>A plain unit test on the parser. The failure this guards against only shows up in a
 * real deployment, on first boot, as {@code 'url' must start with "jdbc"} — an error that
 * names neither the variable nor the provider that produced it.
 */
class DatabaseUrlTest {

    @Test
    void convertsARenderStyleUrl() {
        Parsed parsed = DatabaseUrl.parse(
                "postgres://aisa:s3cret@dpg-abc123.singapore-postgres.render.com/aisa_db");

        assertThat(parsed).isNotNull();
        // Port defaulted to 5432 — Render's connection string omits it.
        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://dpg-abc123.singapore-postgres.render.com:5432/aisa_db");
        assertThat(parsed.username()).isEqualTo("aisa");
        assertThat(parsed.password()).isEqualTo("s3cret");
    }

    @Test
    void keepsAnExplicitPort() {
        assertThat(DatabaseUrl.parse("postgresql://user:pw@db.example.org:6543/postgres").jdbcUrl())
                .isEqualTo("jdbc:postgresql://db.example.org:6543/postgres");
    }

    /**
     * Supabase and Neon both require sslmode=require. Dropping the query string turns a
     * working URL into a connection refused, which looks like a firewall problem.
     */
    @Test
    void preservesTheQueryString() {
        assertThat(DatabaseUrl.parse("postgres://u:p@ep-cool.neon.tech/neondb?sslmode=require").jdbcUrl())
                .endsWith("/neondb?sslmode=require");
    }

    /**
     * Generated passwords routinely contain characters that must be percent-encoded.
     * Passing the encoded form through authenticates with the wrong password.
     */
    @Test
    void decodesAPercentEncodedPassword() {
        assertThat(DatabaseUrl.parse("postgres://user:p%40ss%2Fword@host/db").password())
                .isEqualTo("p@ss/word");
    }

    @Test
    void leavesAJdbcUrlAlone() {
        // Local development and the Testcontainers suite must keep working untouched.
        assertThat(DatabaseUrl.parse("jdbc:postgresql://localhost:5432/aisa")).isNull();
    }

    @Test
    void ignoresSomethingItDoesNotUnderstand() {
        assertThat(DatabaseUrl.parse("mysql://host/db")).isNull();
        assertThat(DatabaseUrl.parse("not a url at all")).isNull();
    }
}
