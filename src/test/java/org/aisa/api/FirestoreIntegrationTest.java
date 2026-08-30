package org.aisa.api;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.aisa.api.user.AccountStatus;
import org.aisa.api.user.AppUser;
import org.aisa.api.user.UserRepository;
import org.aisa.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for tests that need the real application context, a real Firestore and real
 * Firebase Auth.
 *
 * <p>Runs against the <b>emulators</b>, started by the test harness (see README:
 * {@code npx firebase-tools emulators:start --only firestore,auth}). They are the only
 * honest way to test this layer: the point of the rewrite is the behaviour of queries,
 * batched writes, the hand-rolled referential integrity and the token filter, none of
 * which a mocked client would exercise — a mock would happily confirm that the code calls
 * the methods it calls.
 *
 * <h2>The two emulator variables are set differently, and have to be</h2>
 *
 * <p>{@code FIRESTORE_EMULATOR_HOST} is read by our own {@code FirestoreConfig} through
 * Spring, so {@link DynamicPropertySource} can supply it. {@code FIREBASE_AUTH_EMULATOR_HOST}
 * is read by the Firebase Admin SDK itself, from {@code System.getenv}, which cannot be
 * written from inside a running JVM — so it is set on the forked test JVM by the Surefire
 * configuration in {@code pom.xml}. Setting it here would compile, do nothing, and leave
 * every token being verified against Google's real public keys.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class FirestoreIntegrationTest {

    static final String FIRESTORE_EMULATOR = System.getenv().getOrDefault(
            "FIRESTORE_EMULATOR_HOST", "127.0.0.1:8085");

    static final String AUTH_EMULATOR = System.getenv().getOrDefault(
            "FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099");

    protected static final String ADMIN_EMAIL = "admin@bsiet.org";
    protected static final String STUDENT_EMAIL = "student@bsiet.org";
    protected static final String PASSWORD = "test-account-password";

    @Autowired
    protected Firestore firestore;

    @Autowired
    protected FirebaseAuth firebaseAuth;

    @Autowired
    protected UserRepository users;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("FIRESTORE_EMULATOR_HOST", () -> FIRESTORE_EMULATOR);
        registry.add("aisa.firebase.project-id", () -> "aisa-local");
        // Empty, so AdminBootstrapper does not race the per-test setup below.
        registry.add("aisa.admin.bootstrap-email", () -> "");
        registry.add("aisa.admin.bootstrap-password", () -> "");
    }

    /**
     * Wipes every collection and rebuilds the two accounts the tests sign in as.
     *
     * <p>The emulators keep data for the life of the process, so without the wipe the
     * seeder's "is this collection empty?" guard would be true only for whichever test ran
     * first, and every other test would depend on execution order.
     */
    @BeforeEach
    void resetEmulators() throws Exception {
        for (String collection : List.of("committees", "members", "events", "gallery",
                "achievements", "messages", "settings", "users",
                "eventRegistrations", "committeeApplications")) {
            List<QueryDocumentSnapshot> docs =
                    firestore.collection(collection).get().get().getDocuments();
            for (QueryDocumentSnapshot doc : docs) {
                doc.getReference().delete().get();
            }
        }

        // The Firebase accounts persist across the wipe (they are not Firestore), so these
        // are created once and reused. Only the profiles below need rebuilding.
        AppUser admin = profileFor(ensureFirebaseUser(ADMIN_EMAIL), UserRole.ADMIN);
        AppUser student = profileFor(ensureFirebaseUser(STUDENT_EMAIL), UserRole.STUDENT);
        users.save(admin);
        users.save(student);
    }

    private AppUser profileFor(UserRecord record, UserRole role) {
        AppUser user = new AppUser(record.getUid(), record.getEmail(), role.name() + " Account");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    private UserRecord ensureFirebaseUser(String email) throws FirebaseAuthException {
        try {
            return firebaseAuth.getUserByEmail(email);
        } catch (FirebaseAuthException notFound) {
            return firebaseAuth.createUser(new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(PASSWORD)
                    // Verified, or every signed-in test would land on ROLE_UNVERIFIED.
                    // The unverified case gets its own explicit test instead.
                    .setEmailVerified(true));
        }
    }

    // ── Signing in ───────────────────────────────────────────────────────────────

    protected HttpHeaders adminAuth() {
        return bearer(idTokenFor(ADMIN_EMAIL));
    }

    protected HttpHeaders studentAuth() {
        return bearer(idTokenFor(STUDENT_EMAIL));
    }

    protected static HttpHeaders bearer(String idToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(idToken);
        return headers;
    }

    /**
     * Mints a real ID token by signing in against the Auth emulator.
     *
     * <p>The Admin SDK can create accounts but cannot sign in as one — issuing an ID token
     * is a client operation. The emulator exposes the same Identity Toolkit endpoint the
     * real service does and accepts any API key, so this is the genuine sign-in path the
     * browser uses, exercising the filter exactly as production will.
     */
    protected static String idTokenFor(String email) {
        String body = """
                {"email":"%s","password":"%s","returnSecureToken":true}
                """.formatted(email, PASSWORD);
        URI uri = URI.create("http://" + AUTH_EMULATOR
                + "/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=any");
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Auth emulator refused the sign-in for " + email + ": " + response.body());
            }
            return extract(response.body(), "idToken");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(
                    "Could not reach the Firebase Auth emulator at " + AUTH_EMULATOR
                            + ". Start it with: npx firebase-tools emulators:start --only firestore,auth",
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while signing in", ex);
        }
    }

    /** Enough JSON parsing for one flat field, rather than a dependency for one line. */
    private static String extract(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            throw new IllegalStateException("No " + field + " in the emulator response: " + json);
        }
        start += needle.length();
        return json.substring(start, json.indexOf('"', start));
    }
}
