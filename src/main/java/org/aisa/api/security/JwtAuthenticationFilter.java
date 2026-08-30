package org.aisa.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <token>} and, if it verifies, populates the security
 * context with {@code ROLE_ADMIN}.
 *
 * <p>A missing or invalid token is not rejected here — the request simply stays anonymous
 * and {@link org.aisa.api.config.SecurityConfig} decides whether that is allowed. Public
 * GETs must keep working for visitors who have no token at all.
 */
@Component
@Order(1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> ADMIN_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            jwtService.verify(token).ifPresent(admin -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        admin, null, ADMIN_AUTHORITIES);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        chain.doFilter(request, response);
    }

    /**
     * Skips the health probe and the API docs.
     *
     * <p>Not a security decision — those paths are already permitAll. It keeps the
     * platform's health check from doing signature verification several times a minute
     * for the lifetime of the deployment.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health")
                || path.startsWith("/docs")
                || path.startsWith("/v3/api-docs");
    }
}
