package com.rfid.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point for the RFID Reader Integration application.
 *
 * CHANGED:
 * - Extended SpringBootServletInitializer for WAR deployment on external Tomcat.
 *   Override configure() registers the application class with the servlet container.
 * - main() is retained for embedded execution during development.
 */
@SpringBootApplication
public class RfidApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(RfidApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(RfidApplication.class, args);
    }
}
