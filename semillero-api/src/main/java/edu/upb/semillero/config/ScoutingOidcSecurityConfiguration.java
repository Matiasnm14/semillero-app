package edu.upb.semillero.config;

import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Optional OIDC chain. External identities are resolved to existing users and database roles only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "semillero.scouting.oidc.enabled", havingValue = "true")
public class ScoutingOidcSecurityConfiguration {
    private final String issuer;
    private final String audience;

    public ScoutingOidcSecurityConfiguration(@Value("${semillero.scouting.oidc.issuer-uri:}") String issuer,
            @Value("${semillero.scouting.oidc.audience:}") String audience) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("SCOUTING_OIDC_ENABLED requiere SCOUTING_OIDC_ISSUER_URI y SCOUTING_OIDC_AUDIENCE");
        }
        this.issuer = issuer;
        this.audience = audience;
    }

    @Bean
    JwtDecoder scoutingJwtDecoder() {
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "La audiencia del token OIDC no corresponde a scouting", null));
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder)
                .setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> scoutingOidcAuthenticationConverter(
            JdbcTemplate jdbc, ComunService comun) {
        return jwt -> {
            String tokenIssuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            String subject = jwt.getSubject();
            if (!issuer.equals(tokenIssuer) || subject == null || subject.isBlank()) {
                throw new BadCredentialsException("Identidad OIDC incompleta");
            }
            Long userId = jdbc.query("SELECT users_id FROM scouting_oidc_identities WHERE issuer=? AND subject=? AND active",
                    rows -> rows.next() ? rows.getLong(1) : null, issuer, subject);
            if (userId == null) throw new BadCredentialsException("La identidad OIDC no está vinculada a una cuenta Semillero");
            List<String> roles = List.copyOf(comun.rolesDe(userId));
            if (roles.stream().noneMatch(role -> List.of("SCOUT", "COORDINADOR_SCOUTING", "RECLUTADOR", "EVALUADOR",
                    "RESPONSABLE_SALVAGUARDA", "SCOUTING_AUDITOR", "SCOUTING_CONFIG", "SCOUTING_STATUS").contains(role))) {
                throw new BadCredentialsException("La cuenta vinculada no tiene roles de scouting");
            }
            String name = jdbc.query("SELECT coalesce(name,email) FROM users WHERE id=?", rows -> rows.next() ? rows.getString(1) : null, userId);
            if (name == null) throw new BadCredentialsException("La cuenta Semillero vinculada ya no existe");
            var principal = new UsuarioAutenticado(userId, name, roles, null);
            var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain scoutingOidcChain(HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> scoutingOidcAuthenticationConverter) throws Exception {
        RequestMatcher allScoutingRoutes = request -> request.getRequestURI().startsWith("/api/v1/scouting/");
        RequestMatcher adminMappingRoute = request -> request.getRequestURI().startsWith("/api/v1/scouting/config/oidc-identities");
        var scoutingExceptAdminMapping = new AndRequestMatcher(allScoutingRoutes,
                new NegatedRequestMatcher(adminMappingRoute));
        return http.securityMatcher(scoutingExceptAdminMapping)
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/v1/scouting/audit-events")
                        .hasAnyRole("SCOUTING_AUDITOR", "COORDINADOR_SCOUTING")
                        .requestMatchers("/api/v1/scouting/**")
                        .hasAnyRole("SCOUT", "COORDINADOR_SCOUTING", "RECLUTADOR", "EVALUADOR",
                                "RESPONSABLE_SALVAGUARDA", "SCOUTING_AUDITOR", "SCOUTING_CONFIG", "SCOUTING_STATUS"))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(scoutingOidcAuthenticationConverter)))
                .build();
    }
}
