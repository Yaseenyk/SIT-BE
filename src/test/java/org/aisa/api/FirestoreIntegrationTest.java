package org.aisa.api;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.List;
import org.aisa.api.auth.AdminUser;
import org.aisa.api.auth.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for tests that need the real application context and a real Firestore.
 *
 * <p>Runs against the <b>Firestore emulator</b>, started by the test harness (see
 * README: {@code npx firebase-tools emulators:start --only firestore}). The emulator is the
 * only honest way to test this layer: the whole point of the rewrite is the behaviour of
 * queries, batched writes and the hand-rolled referential integrity, none of which a mocked
 * {@code Firestore} would exercise — a mock would happily confirm that the code calls the
 * methods it calls.
 *
 * <p>{@code FIRESTORE_EMULATOR_HOST} is set as a system property, which
 * {@link org.aisa.api.firestore.FirestoreConfig} treats as an absolute switch: with it set,
 * no credentials are read and nothing can reach the real project. A test that accidentally
 * ran against production would be far worse than a test that fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class FirestoreIntegrationTest {

    static final String EMULATOR = System.getenv().getOrDefault(
            "FIRESTORE_EMULATOR_HOST", "127.0.0.1:8085");

    @Autowired
    protected Firestore firestore;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("FIRESTORE_EMULATOR_HOST", () -> EMULATOR);
        registry.add("aisa.firebase.project-id", () -> "aisa-local");
        // A valid base64 key of the right length. Production reads this from the
        // environment; tests must not depend on that being set.
        registry.add("aisa.jwt.secret",
                () -> "dGVzdC1vbmx5LXNpZ25pbmcta2V5LWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=");
        registry.add("aisa.admin.bootstrap-password", () -> "test-admin-password");
    }

    @Autowired
    private AdminUserRepository admins;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Wipes every collection, then recreates the admin account.
     *
     * <p>The emulator keeps data for the life of the process, so without the wipe the
     * seeder's "is this collection empty?" guard would be true only for whichever test ran
     * first and every other test would depend on execution order.
     *
     * <p>The admin has to be put back by hand because {@code AdminBootstrapper} runs once,
     * at context startup — the wipe happens long after. Recreating it here also resets the
     * lockout counters, so a test that deliberately fails five logins cannot leave the
     * account locked for the next one.
     */
    @BeforeEach
    void resetFirestore() throws Exception {
        for (String collection : List.of("committees", "members", "events", "gallery",
                "achievements", "messages", "settings", "adminUsers")) {
            List<QueryDocumentSnapshot> docs =
                    firestore.collection(collection).get().get().getDocuments();
            for (QueryDocumentSnapshot doc : docs) {
                doc.getReference().delete().get();
            }
        }
        admins.save(new AdminUser("AISA2026", passwordEncoder.encode("test-admin-password")));
    }
}
