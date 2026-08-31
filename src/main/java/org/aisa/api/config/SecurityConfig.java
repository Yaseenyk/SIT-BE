package org.aisa.api.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.aisa.api.security.FirebaseAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Three audiences, one file.
 *
 * <p>Visitors read the public site. Students, once signed in and verified, register for
 * events and apply to committees. Admins do everything else. That sentence is the whole
 * authorisation model, and it is expressed here as rules rather than as annotations
 * sprinkled across forty handler methods — where one forgotten annotation is an open
 * endpoint that no test would notice.
 *
 * <p>The ordering rule that makes this readable: most specific first, and
 * {@code anyRequest().hasRole("ADMIN")} last. Anything nobody thought about is therefore
 * admin-only, so the failure mode of forgetting a rule is a 403 somebody reports rather
 * than a leak nobody sees.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Roles that mean "a real, usable account". Students plus admins. */
    private static final String[] SIGNED_IN = {"STUDENT", "ADMIN"};

    private final AisaProperties properties;
    private final FirebaseAuthenticationFilter firebaseFilter;

    public SecurityConfig(AisaProperties properties, FirebaseAuthenticationFilter firebaseFilter) {
        this.properties = properties;
        this.firebaseFilter = firebaseFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                /*
                 * CSRF protection defends session cookies. This API has none — the token
                 * travels in an Authorization header the browser never attaches on its
                 * own, so a cross-site form post cannot carry it. Disabling here is the
                 * documented stateless-API case, not a shortcut.
                 */
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        // 401 for "no or bad token", so the frontend can send the caller
                        // to the login screen rather than showing a generic failure.
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        /*
                         * The container's ERROR dispatch, not a URL anyone can request.
                         *
                         * When a rule below denies a request, Spring Security answers 403
                         * and the container then re-dispatches internally to /error to
                         * render the body. That second pass goes through this chain again,
                         * with the security context already cleared — so it matches the
                         * anonymous case, is denied by the catch-all at the bottom, and the
                         * entry point rewrites the 403 into a 401.
                         *
                         * The effect was that EVERY error came back as 401: a student hitting
                         * an admin endpoint looked signed-out rather than unauthorised, and a
                         * 404 or a 500 would have claimed the same. It stayed hidden while
                         * admin-or-anonymous were the only two states, because a denial was
                         * always anonymous and 401 happened to be the right answer.
                         *
                         * Matching the dispatcher type rather than permitting the "/error"
                         * path keeps it unreachable from outside — a direct GET /error is
                         * a REQUEST dispatch and still falls through to the rule at the end.
                         */
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/docs/**", "/v3/api-docs/**").permitAll()

                        // The contact form is the one write a visitor may perform. It is
                        // rate-limited by IP in ContactMessageService instead.
                        .requestMatchers(HttpMethod.POST, "/api/v1/messages").permitAll()

                        /*
                         * Public reads are ENUMERATED, not expressed as a blanket
                         * `GET /api/v1/**` with the private ones carved out above it.
                         *
                         * The blanket form was wrong, and wrong in the silent direction:
                         * `GET /api/v1/settings/admin` matched it and served the staff
                         * notification address to anonymous callers.
                         *
                         * No trailing wildcard on settings and stats: their `/admin`
                         * variants must fall through to the rule at the bottom.
                         */
                        /*
                         * BEFORE the public list below, and that ordering is the whole
                         * point: `/api/v1/members/*` is permitAll, and `/members/admin`
                         * matches it. Without this line the admin roster — every
                         * volunteer's personal mobile number and mail address — would be
                         * served to anonymous callers by a rule written for `/members/{id}`.
                         *
                         * This is the same shape as the bug that once published the staff
                         * notification address through a blanket `GET /api/v1/**`. Spring
                         * Security takes the FIRST match, so a deny placed first wins.
                         */
                        .requestMatchers(HttpMethod.GET, "/api/v1/members/admin").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/committees", "/api/v1/committees/*",
                                "/api/v1/members", "/api/v1/members/*",
                                "/api/v1/events", "/api/v1/events/*",
                                "/api/v1/gallery", "/api/v1/gallery/albums/*",
                                "/api/v1/achievements",
                                "/api/v1/settings",
                                "/api/v1/stats").permitAll()

                        /*
                         * Registration and /auth/me are the only endpoints reachable by an
                         * account that is not yet usable, and they have to be: a caller who
                         * is unregistered, unverified or suspended needs to be TOLD which
                         * of those they are. Locking these behind ROLE_STUDENT would leave
                         * them with a 403 and no way to find out why.
                         *
                         * `/auth/session` is here for the same reason and NOT with the
                         * signed-in endpoints below, where it started. The frontend calls it
                         * once on every load to find out who the caller is; behind
                         * ROLE_STUDENT it answered an unverified account with 403, the
                         * client read the failure as "no profile", and the app reported a
                         * verified-address problem as an unregistered account — the one
                         * state it could not then explain or recover from.
                         *
                         * `authenticated()`, not permitAll — a valid Firebase token is
                         * still required, and UserService decides the rest.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/session").authenticated()

                        /*
                         * Signed-in students. Every one of these acts on behalf of the
                         * caller and takes the uid from the security context rather than
                         * the request body, so there is no id here for anyone to tamper
                         * with — see CurrentUser.
                         */
                        .requestMatchers(HttpMethod.PUT, "/api/v1/auth/profile").hasAnyRole(SIGNED_IN)
                        .requestMatchers("/api/v1/me/**").hasAnyRole(SIGNED_IN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registration").hasAnyRole(SIGNED_IN)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/events/*/registration").hasAnyRole(SIGNED_IN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasAnyRole(SIGNED_IN)

                        /*
                         * A student may upload their own profile photo, so signing an
                         * upload cannot be admin-only. MediaService pins the folder and the
                         * transformation into the signature, so this grants the ability to
                         * upload one resized image — not to write anywhere in the account.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/v1/media/signature").hasAnyRole(SIGNED_IN)

                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        /*
         * Exact origins only. setAllowedOriginPatterns("*") would let any page on the
         * internet read admin responses from a browser that holds a valid token.
         */
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
