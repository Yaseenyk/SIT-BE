package org.aisa.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The authorisation model, asserted end to end.
 *
 * <p>This is the test that matters most in this codebase. The rules live in one place
 * ({@code SecurityConfig}), and the failure mode of getting them wrong is not an exception
 * anyone would notice — it is a write endpoint that quietly works for anonymous callers.
 */
class SecurityRulesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void publicReadsAreOpen() {
        for (String path : new String[]{
                "/api/v1/committees", "/api/v1/members", "/api/v1/events",
                "/api/v1/gallery", "/api/v1/achievements", "/api/v1/settings", "/api/v1/stats"}) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("GET %s should be readable by a visitor", path)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void writesAreRejectedWithoutAToken() {
        assertThat(rest.postForEntity("/api/v1/committees", Map.of("id", "x", "name", "X", "type", "functional"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(rest.exchange("/api/v1/members/" + java.util.UUID.randomUUID(),
                HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The admin-only GETs.
     *
     * <p>This test exists because an earlier version of {@code SecurityConfig} used a
     * blanket {@code GET /api/v1/**} permitAll with the private paths carved out above it,
     * and {@code /settings/admin} was not one of them — so the staff notification address
     * was served to anonymous callers, with no error anywhere to reveal it.
     *
     * <p>Every one of these returns 200 to an admin, so a broken rule here is invisible in
     * the dashboard. This is the only thing that catches it.
     */
    @Test
    void adminOnlyReadsRejectAnonymousCallers() {
        for (String path : new String[]{
                "/api/v1/messages", "/api/v1/settings/admin", "/api/v1/stats/admin"}) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("GET %s must not be readable without a token", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void theContactFormIsPublic() {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/messages", Map.of(
                "name", "Visitor",
                "email", "visitor@example.com",
                "message", "Is registration open?"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    /**
     * The notification address is admin-only, and this asserts it on a real VALUE rather
     * than on the field name.
     *
     * <p>Jackson is configured to omit nulls, so on a fresh database the field is absent
     * from both responses and an assertion on the name alone would pass while proving
     * nothing. Setting one first is what makes the leak detectable.
     */
    @Test
    void theNotificationAddressIsAdminOnly() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(signIn());
        headers.setContentType(MediaType.APPLICATION_JSON);

        String secret = "secretary@bsiet.example.org";
        ResponseEntity<String> saved = rest.exchange(
                "/api/v1/settings", HttpMethod.PUT,
                new HttpEntity<>(Map.of("notificationEmail", secret), headers), String.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(rest.getForObject("/api/v1/settings", String.class))
                .as("the public settings endpoint must never publish the staff address")
                .doesNotContain(secret);

        ResponseEntity<String> adminView = rest.exchange(
                "/api/v1/settings/admin", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders(headers)), String.class);
        assertThat(adminView.getBody()).contains(secret);
    }

    private String signIn() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.postForObject("/api/v1/auth/login",
                Map.of("username", "AISA2026", "password", "test-admin-password"), Map.class);
        return (String) body.get("token");
    }
}
