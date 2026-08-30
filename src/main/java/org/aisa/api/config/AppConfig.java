package org.aisa.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Small application-wide beans.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "AISA API",
        version = "1.0.0",
        description = "Backend for the AIML Student Association site, BSIET Kolhapur."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class AppConfig {

    /**
     * The clock everything date-dependent reads.
     *
     * <p>Fixed to Asia/Kolkata rather than the host's zone: the container runs in UTC, and
     * "is this event still upcoming?" must flip over at midnight in Kolhapur, not five and
     * a half hours later. Injecting it also lets tests pin a date instead of depending on
     * when the suite happens to run.
     */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }
}
