package org.aisa.api;

import org.aisa.api.config.AisaProperties;
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
        SpringApplication.run(AisaApiApplication.class, args);
    }
}
