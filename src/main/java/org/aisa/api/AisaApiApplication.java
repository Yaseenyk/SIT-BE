package org.aisa.api;

import org.aisa.api.config.AisaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AISA API — the backend for the AIML Student Association site at BSIET Kolhapur.
 *
 * <p>Data lives in the same Firestore project the original single-file site used. What
 * changed is who talks to it: the browser no longer holds credentials and no longer writes
 * directly. Every write goes through this service, which holds the service-account key and
 * decides who is allowed to make it — so authorisation is enforced somewhere the client
 * cannot reach, rather than in Firestore rules guarding a client that holds the keys.
 */
@SpringBootApplication
@EnableConfigurationProperties(AisaProperties.class)
public class AisaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AisaApiApplication.class, args);
    }
}
