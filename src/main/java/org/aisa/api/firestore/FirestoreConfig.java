package org.aisa.api.firestore;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
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
 * Builds the {@link Firestore} client.
 *
 * <p>Credentials are resolved in this order, and the order is the point:
 *
 * <ol>
 *   <li>{@code FIRESTORE_EMULATOR_HOST} — tests and local development. No credentials at
 *       all, so a misconfigured test can never reach the real project and write to it.
 *   <li>{@code FIREBASE_SERVICE_ACCOUNT} — the service-account JSON, raw or base64. This
 *       is what production uses: Render exposes secrets as environment variables, and a
 *       JSON blob with newlines does not survive most dashboards intact, hence base64.
 *   <li>Application Default Credentials — for running on Google infrastructure.
 * </ol>
 *
 * <p>The service-account JSON is a private key. It is never logged, never written to disk,
 * and never committed; {@code .env} is git-ignored and {@code .env.example} carries only
 * the shape.
 */
@Configuration
public class FirestoreConfig {

    private static final Logger log = LoggerFactory.getLogger(FirestoreConfig.class);

    private final String projectId;
    private final String serviceAccount;
    private final String emulatorHost;

    public FirestoreConfig(
            @Value("${aisa.firebase.project-id:}") String projectId,
            @Value("${aisa.firebase.service-account:}") String serviceAccount,
            @Value("${FIRESTORE_EMULATOR_HOST:}") String emulatorHost) {
        this.projectId = projectId;
        this.serviceAccount = serviceAccount;
        this.emulatorHost = emulatorHost;
    }

    @Bean(destroyMethod = "close")
    Firestore firestore() throws IOException {
        FirestoreOptions.Builder options = FirestoreOptions.newBuilder();

        if (!emulatorHost.isBlank()) {
            log.warn("Using the Firestore EMULATOR at {} — no data reaches the real project.",
                    emulatorHost);
            /*
             * setHost, NOT setEmulatorHost.
             *
             * FirestoreOptions.setEmulatorHost() is silently ignored by this version of the
             * SDK — the client keeps dialling firestore.googleapis.com, which answers a
             * plaintext HTTP/2 preface with a TLS alert. That surfaces as
             * "First received frame was not SETTINGS", an error naming neither TLS, nor the
             * emulator, nor the endpoint it actually used. It was only provable by pointing
             * the client at a port with nothing on it and getting the identical error.
             *
             * setHost takes a bare "host:port" — with a scheme it fails on
             * "Could not find a NameResolverProvider for http://…". Combined with
             * NoCredentials the channel is plaintext, which is what the emulator serves.
             *
             * NoCredentials is also the safety property: with no credentials this client
             * cannot reach the real project even if the host were wrong.
             */
            options.setHost(emulatorHost)
                    .setCredentials(com.google.cloud.NoCredentials.getInstance())
                    .setProjectId(projectId.isBlank() ? "aisa-local" : projectId);
            return options.build().getService();
        }

        if (!serviceAccount.isBlank()) {
            byte[] json = decode(serviceAccount);
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            options.setCredentials(credentials);
            if (!projectId.isBlank()) {
                options.setProjectId(projectId);
            }
            log.info("Firestore configured from FIREBASE_SERVICE_ACCOUNT.");
            return options.build().getService();
        }

        /*
         * No explicit credentials. This works on Google infrastructure and fails
         * everywhere else — deliberately at startup, with this message, rather than on the
         * first request with a generic authentication error.
         */
        log.warn("No FIREBASE_SERVICE_ACCOUNT set; falling back to application default "
                + "credentials. Outside Google Cloud this will fail on first use.");
        options.setCredentials(GoogleCredentials.getApplicationDefault());
        if (!projectId.isBlank()) {
            options.setProjectId(projectId);
        }
        return options.build().getService();
    }

    /**
     * Accepts the service-account JSON either raw or base64-encoded.
     *
     * <p>Base64 exists because the JSON contains a PEM private key full of newlines, and
     * pasting that into a hosting dashboard's single-line environment-variable field
     * mangles it — producing an "invalid key" error that points nowhere near the cause.
     */
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
