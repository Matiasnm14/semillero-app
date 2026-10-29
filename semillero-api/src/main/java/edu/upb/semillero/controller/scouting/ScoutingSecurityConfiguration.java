package edu.upb.semillero.controller.scouting;

import edu.upb.semillero.config.JwtAutenticacionFilter;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Cadena limitada al prefijo de scouting. Conserva las reglas de los demás módulos. */
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "scoutingBearer", type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@Configuration
public class ScoutingSecurityConfiguration {
    @Bean @Order(1)
    SecurityFilterChain scoutingSecurity(HttpSecurity http, JwtAutenticacionFilter jwt) throws Exception {
        return http.securityMatcher("/api/v1/scouting/**")
            .cors(Customizer.withDefaults()).csrf(c -> c.disable())
            .httpBasic(c -> c.disable()).formLogin(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c.anyRequest().authenticated())
            .exceptionHandling(c -> c.authenticationEntryPoint((request, response, exception) -> {
                response.setStatus(401); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Hace falta iniciar sesión con un token válido\"}");
            }))
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
}
