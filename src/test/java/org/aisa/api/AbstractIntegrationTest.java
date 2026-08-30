package org.aisa.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the real application context and a real database.
 *
 * <p>Postgres in a container rather than H2. The schema uses a partial index
 * ({@code WHERE is_read = FALSE}), {@code TIMESTAMPTZ}, {@code gen_random_uuid()} and
 * {@code NULLS LAST} ordering — H2 accepts some of those and silently behaves differently
 * on the rest, so a green suite would say nothing about whether production works.
 *
 * <p>The container is {@code static}, so one Postgres is started for the whole suite
 * rather than one per test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // A valid base64 key of the right length. The production value comes from the
        // environment; tests must not depend on that being set.
        registry.add("aisa.jwt.secret",
                () -> "dGVzdC1vbmx5LXNpZ25pbmcta2V5LWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=");
        registry.add("aisa.admin.bootstrap-password", () -> "test-admin-password");
    }
}
