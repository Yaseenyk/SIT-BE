package org.aisa.api.config;

import java.util.List;
import org.aisa.api.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The site is public to read and admin-only to write.
 *
 * <p>That single sentence is the whole authorisation model, and it is expressed here as
 * "GET is open, everything else needs the admin role" rather than as an annotation on
 * each of the ~30 handler methods — where one forgotten annotation is an open write
 * endpoint that no test would notice.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AisaProperties properties;
    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(AisaProperties properties, JwtAuthenticationFilter jwtFilter) {
        this.properties = properties;
        this.jwtFilter = jwtFilter;
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
                        // 401 for "no or bad token", so the frontend can send the admin
                        // back to the login screen rather than showing a generic failure.
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // The contact form is the one write a visitor may perform. It is
                        // rate-limited by IP in ContactMessageService instead.
                        .requestMatchers(HttpMethod.POST, "/api/v1/messages").permitAll()

                        /*
                         * Public reads are ENUMERATED, not expressed as a blanket
                         * `GET /api/v1/**` with the private ones carved out above it.
                         *
                         * The blanket form was wrong, and wrong in the silent direction:
                         * `GET /api/v1/settings/admin` matched it and served the staff
                         * notification address to anonymous callers. Listing the public
                         * paths means a new admin GET is private by default — the failure
                         * mode becomes a 401 somebody reports, not a leak nobody sees.
                         *
                         * No trailing wildcard on settings and stats: their `/admin`
                         * variants must fall through to the rule at the bottom.
                         */
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/committees", "/api/v1/committees/*",
                                "/api/v1/members", "/api/v1/members/*",
                                "/api/v1/events", "/api/v1/events/*",
                                "/api/v1/gallery", "/api/v1/gallery/albums/*",
                                "/api/v1/achievements",
                                "/api/v1/settings",
                                "/api/v1/stats").permitAll()

                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

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

    @Bean
    PasswordEncoder passwordEncoder() {
        // Strength 12: ~250ms per hash on free-tier hardware. Slow enough to make
        // offline cracking expensive, fast enough that a login does not feel broken.
        return new BCryptPasswordEncoder(12);
    }
}
