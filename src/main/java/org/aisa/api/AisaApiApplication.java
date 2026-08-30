package org.aisa.api;

import org.aisa.api.config.AisaProperties;
import org.aisa.api.config.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * AISA API — the backend for the AIML Student Association site at BSIET Kolhapur.
 *
 * <p>Replaces the Firebase (Firestore + Auth + Storage) calls the previous single-file
 * site made directly from the browser. The important difference is not the database: it
 * is that write authorisation now happens on a server the browser cannot bypass, rather
 * than in Firestore rules guarding a client that holds the credentials.
 */
@SpringBootApplication
@EnableConfigurationProperties(AisaProperties.class)
@EnableJpaAuditing
public class AisaApiApplication {

    public static void main(String[] args) {
        /*
         * Before the context exists: managed Postgres providers hand out
         * postgres://user:pass@host/db, which the JDBC driver rejects. Converting it here
         * means DATABASE_URL can be wired straight from the platform with no manual step.
         * A jdbc: URL passes through untouched.
         */
        DatabaseUrl.applyFromEnvironment(System.getProperties());

        SpringApplication.run(AisaApiApplication.class, args);
    }
}
