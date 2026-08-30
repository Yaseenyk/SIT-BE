package org.aisa.api.firestore;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link FirebaseAuth} client — the identity half of the same Firebase project
 * {@link FirestoreConfig} reads data from.
 *
 * <h2>Why Firebase Auth at all</h2>
 *
 * <p>Because "forgot my password" has to send an email, and everything else follows from
 * who sends it. Rolling our own would mean SMTP credentials, a reset-token collection with
 * hashing, expiry and single-use semantics, and ownership of deliverability — a reset mail
 * that lands in spam is a reset feature that does not work, and there would be no signal
 * that it had happened. Firebase sends both the reset and the verification mail from
 * Google's infrastructure with no configuration at all.
 *
 * <p>This is not a return to what the original single-file site did. That site's mistake
 * was deciding <em>permissions</em> in the browser. Here Firebase answers only "who is
 * this", and every question about what they may do is still answered by
 * {@link org.aisa.api.config.SecurityConfig} on the server.
 *
 * <h2>Credentials</h2>
 *
 * <p>The same service account as Firestore, resolved the same way and for the same
 * reasons — see {@link FirestoreConfig}, which documents the base64 encoding and why the
 * emulator branch takes no credentials at all.
 */
@Configuration
public class FirebaseAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthConfig.class);

    /** The name of the emulator's env var, which the Admin SDK reads for itself. */
    private static final String AUTH_EMULATOR_ENV = "FIREBASE_AUTH_EMULATOR_HOST";

    private final String projectId;
    private final String serviceAccount;

    public FirebaseAuthConfig(
            @Value("${aisa.firebase.project-id:}") String projectId,
            @Value("${aisa.firebase.service-account:}") String serviceAccount) {
        this.projectId = projectId;
        this.serviceAccount = serviceAccount;
    }

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        /*
         * FirebaseApp.initializeApp throws if the default app already exists, and in tests
         * the context is built more than once in the same JVM. Returning the existing
         * instance is correct rather than merely tolerant: there is one project, so there
         * is one app.
         */
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String emulator = System.getenv(AUTH_EMULATOR_ENV);
        if (emulator != null && !emulator.isBlank()) {
            /*
             * The Admin SDK picks FIREBASE_AUTH_EMULATOR_HOST up on its own and then skips
             * signature verification entirely — which is exactly why this branch must never
             * be reachable in production. It is guarded by the variable simply not being
             * set there, the same way the Firestore emulator branch is.
             */
            log.warn("Using the Firebase Auth EMULATOR at {} — ID token signatures are NOT verified.",
                    emulator);
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder();
        if (!projectId.isBlank()) {
            options.setProjectId(projectId);
        }

        if (!serviceAccount.isBlank()) {
            options.setCredentials(GoogleCredentials.fromStream(
                    new ByteArrayInputStream(decode(serviceAccount))));
        } else if (emulator != null && !emulator.isBlank()) {
            // The emulator accepts anything; supplying real credentials to it would be
            // both pointless and a way for a misconfigured test to reach the real project.
            options.setCredentials(com.google.auth.oauth2.GoogleCredentials.newBuilder().build());
        } else {
            log.warn("No FIREBASE_SERVICE_ACCOUNT set; Firebase Auth is falling back to "
                    + "application default credentials. Outside Google Cloud this fails on first use.");
            options.setCredentials(GoogleCredentials.getApplicationDefault());
        }

        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }

    /** Accepts the service-account JSON either raw or base64-encoded, as Firestore does. */
    private static byte[] decode(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{")) {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "FIREBASE_SERVICE_ACCOUNT is neither JSON (starting '{') nor valid base64.", ex);
        }
    }
}
