package com.rfid.integration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Replaces the manual CORSFilter servlet filter.
 *
 * WHY: The old CORSFilter did not handle OPTIONS preflight correctly (no 200 response
 * for preflight requests). Spring's CorsRegistry handles the full CORS lifecycle
 * including preflight, and integrates with Spring Security if added later.
 *
 * NOTE: allowedOrigins("*") is intentionally broad for this internal-network RFID tool.
 * For production with external access, restrict to specific origins.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type");
    }
}
