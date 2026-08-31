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
import com.google.firebase.auth.UserRecord;
import org.springframework.http.ResponseEntity;

/**
 * The authorisation model, asserted end to end.
 *
 * <p>This is the test that matters most in this codebase. The rules live in one place
 * ({@code SecurityConfig}), and the failure mode of getting them wrong is not an exception
 * anyone would notice — it is a write endpoint that quietly works for anonymous callers.
 */
class SecurityRulesIntegrationTest extends FirestoreIntegrationTest {

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

    // ── The account endpoints ────────────────────────────────────────────────────

    /**
     * The rule that would be catastrophic to get wrong.
     *
     * <p>Signup is a public form. If it could ever produce an admin, anyone on the
     * internet could take the site over by filling it in, and nothing would look wrong
     * afterwards — there would simply be an extra admin.
     */
    @Test
    void signingUpCannotProduceAnAdmin() {
        HttpHeaders headers = studentAuth();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // A fresh student, registering. The body carries no role field at all, but assert
        // on the OUTCOME rather than on the absence of the field: a future DTO that added
        // one must fail this test, not quietly start honouring it.
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"role\":\"STUDENT\"");
    }

    /** A student holds a perfectly valid token, and still may not write to the site. */
    @Test
    void studentsCannotReachAdminEndpoints() {
        HttpHeaders headers = studentAuth();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (String path : new String[]{
                "/api/v1/messages", "/api/v1/settings/admin", "/api/v1/stats/admin",
                "/api/v1/admin/users", "/api/v1/applications"}) {
            assertThat(rest.exchange(path, HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders(headers)), String.class)
                    .getStatusCode())
                    .as("GET %s must be admin-only, and a student token is not enough", path)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        assertThat(rest.exchange("/api/v1/committees", HttpMethod.POST,
                        new HttpEntity<>(Map.of("id", "x", "name", "X", "type", "functional"), headers),
                        String.class)
                .getStatusCode())
                .as("a student must not be able to create a committee")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** A student MAY act on their own behalf. The mirror of the test above. */
    @Test
    void studentsCanReachTheirOwnEndpoints() {
        HttpHeaders headers = studentAuth();
        for (String path : new String[]{"/api/v1/me/registrations", "/api/v1/me/applications"}) {
            assertThat(rest.exchange(path, HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders(headers)), String.class)
                    .getStatusCode())
                    .as("GET %s is the caller's own data", path)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void theAccountEndpointsRejectAnonymousCallers() {
        for (String path : new String[]{
                "/api/v1/auth/me", "/api/v1/me/registrations", "/api/v1/me/applications",
                "/api/v1/admin/users"}) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("GET %s must not be readable without a token", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    /** A made-up token must not authenticate anyone. */
    @Test
    void aForgedTokenIsRejected() {
        assertThat(rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                        new HttpEntity<>(bearer("not.a.real.token")), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * An account that cannot act must still be able to find out why.
     *
     * <p>This is the regression test for a bug that made the verification flow
     * unrecoverable. `/auth/session` sat behind ROLE_STUDENT, so an unverified account got
     * 403; the frontend read that failure as "this caller has no profile" and showed the
     * screen for an unregistered account — which offers no way to verify an address.
     *
     * <p>Both endpoints must therefore answer 200 to ANY caller holding a valid token,
     * whatever state their account is in, because the answer is what names the state.
     */
    @Test
    void anUnverifiedAccountCanStillReadItsOwnState() throws Exception {
        String email = "unverified@bsiet.org";
        UserRecord record = ensureUnverifiedFirebaseUser(email);
        users.save(studentProfile(record));

        HttpHeaders headers = bearer(idTokenFor(email));

        for (String path : new String[]{"/api/v1/auth/me"}) {
            ResponseEntity<String> response = rest.exchange(
                    path, HttpMethod.GET, new HttpEntity<>(new HttpHeaders(headers)), String.class);
            assertThat(response.getStatusCode())
                    .as("GET %s must tell an unverified caller what is wrong", path)
                    .isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"state\":\"UNVERIFIED\"");
        }

        assertThat(rest.exchange("/api/v1/auth/session", HttpMethod.POST,
                        new HttpEntity<>(new HttpHeaders(headers)), String.class)
                .getStatusCode())
                .as("POST /auth/session is how the frontend learns the caller's state on load")
                .isEqualTo(HttpStatus.OK);

        // ...and it must still be refused everything that needs a usable account.
        assertThat(rest.exchange("/api/v1/me/registrations", HttpMethod.GET,
                        new HttpEntity<>(new HttpHeaders(headers)), String.class)
                .getStatusCode())
                .as("an unverified account must not be able to act")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * The public roster must never carry a contact detail.
     *
     * <p>Members are imported from an internal selection record of students' personal
     * mobile numbers and mail addresses, and `GET /api/v1/members` is public and rendered
     * on the front page. This asserts on a real VALUE rather than on a field name: Jackson
     * omits nulls, so on an empty database an assertion about "phone" would pass while
     * proving nothing. Writing the member first is what makes a leak detectable.
     *
     * <p>It also pins the routing trap. `/api/v1/members/*` is permitAll, and
     * `/members/admin` matches that pattern — so without an explicit rule ordered before
     * it, the admin roster is served to anyone.
     */
    @Test
    void memberContactDetailsAreNeverPublic() {
        HttpHeaders admin = adminAuth();
        admin.setContentType(MediaType.APPLICATION_JSON);

        String phone = "9699363851";
        String email = "volunteer.contact@example.org";
        assertThat(rest.exchange("/api/v1/members", HttpMethod.POST,
                        new HttpEntity<>(Map.of(
                                "name", "Contact Probe",
                                "role", "Sports Volunteer",
                                "email", email,
                                "phone", phone), admin),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        String publicBody = rest.getForObject("/api/v1/members", String.class);
        assertThat(publicBody)
                .as("the public roster must not publish a volunteer's mobile number")
                .doesNotContain(phone);
        assertThat(publicBody)
                .as("the public roster must not publish a volunteer's email address")
                .doesNotContain(email);

        assertThat(rest.getForEntity("/api/v1/members/admin", String.class).getStatusCode())
                .as("/members/admin matches the permitAll pattern /members/* — it must still be denied")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String adminBody = rest.exchange("/api/v1/members/admin", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders(admin)), String.class).getBody();
        assertThat(adminBody).contains(phone).contains(email);
    }

    /**
     * Uploading is admin-only; the bytes are public.
     *
     * <p>Both halves matter. If POST were public the site would be free image hosting for
     * anyone who found the endpoint, and every upload lands in the same Firestore the rest
     * of the data lives in. If GET were admin-only no gallery photo would render for a
     * visitor, which is the entire point of storing one.
     *
     * <p>The payload is a real 1x1 JPEG: the service checks magic numbers, so a caller
     * cannot label arbitrary bytes image/jpeg and have them served back with that type.
     */
    @Test
    void imagesAreAdminToUploadAndPublicToRead() {
        String jpegBase64 =
                "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a"
                + "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA"
                + "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==";

        HttpHeaders anonymous = new HttpHeaders();
        anonymous.setContentType(MediaType.APPLICATION_JSON);
        assertThat(rest.exchange("/api/v1/images", HttpMethod.POST,
                        new HttpEntity<>(Map.of("data", jpegBase64, "contentType", "image/jpeg",
                                "folder", "gallery"), anonymous),
                        String.class)
                .getStatusCode())
                .as("uploading must not be possible without an admin token")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders admin = adminAuth();
        admin.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> uploaded = rest.exchange("/api/v1/images", HttpMethod.POST,
                new HttpEntity<>(Map.of("data", jpegBase64, "contentType", "image/jpeg",
                        "folder", "gallery"), admin),
                String.class);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = uploaded.getBody();
        assertThat(body).isNotNull();
        String id = body.split("\"id\":\"")[1].split("\"")[0];

        ResponseEntity<byte[]> served = rest.getForEntity("/api/v1/images/" + id, byte[].class);
        assertThat(served.getStatusCode())
                .as("a gallery photo has to render for a visitor with no account")
                .isEqualTo(HttpStatus.OK);
        assertThat(served.getHeaders().getContentType()).hasToString("image/jpeg");
        assertThat(served.getBody()).isNotEmpty();
    }

    /** An SVG is a document that can carry script, and this endpoint serves what it stores. */
    @Test
    void svgUploadsAreRefused() {
        HttpHeaders admin = adminAuth();
        admin.setContentType(MediaType.APPLICATION_JSON);
        String svg = java.util.Base64.getEncoder().encodeToString(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(rest.exchange("/api/v1/images", HttpMethod.POST,
                        new HttpEntity<>(Map.of("data", svg, "contentType", "image/svg+xml",
                                "folder", "gallery"), admin),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String signIn() {
        return idTokenFor(ADMIN_EMAIL);
    }
}
