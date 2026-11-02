package edu.upb.semillero.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Orígenes de Flutter Web configurables; los valores predeterminados son de desarrollo local. */
@Configuration
public class WebCorsConfiguration {
    @Bean
    WebMvcConfigurer corsConfigurer(
            @Value("${semillero.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            String[] origins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins()
                        .allowedOriginPatterns(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
