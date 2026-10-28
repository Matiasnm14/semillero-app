package edu.upb.semillero.config;

import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.seguridad.RegistroAccesoService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * HU 16 - Control de acceso por roles (tareas S2-01, S2-02 y S2-04).
 *
 * <p>Quién puede qué dentro de {@code /api/v1/torneos}:
 *
 * <ul>
 *   <li>leer (GET): cualquier usuario con sesión, incluido el rol CONSULTA;</li>
 *   <li>inscribir un equipo y manejar su lista de buena fe: ENTRENADOR, y
 *       también ORGANIZADOR y ADMIN;</li>
 *   <li>llenar la planilla, cargar los eventos y cerrar el partido:
 *       MESA_CONTROL, y también ORGANIZADOR y ADMIN;</li>
 *   <li>todo lo demás que escribe (torneos, categorías, reglamento, sedes,
 *       canchas, estados de inscripción, fixture, reprogramación y reapertura
 *       de un partido cerrado): ORGANIZADOR o ADMIN.</li>
 * </ul>
 *
 * <p>Las rutas de los otros tres módulos quedan abiertas, igual que antes: esta
 * configuración es de la HU 16 de torneos y no cambia el comportamiento del
 * código de los demás grupos. Cuando ellos quieran cerrar las suyas, se agregan
 * acá.
 *
 * <p>Sin sesión de servidor y sin CSRF porque la API es stateless y se usa con
 * token (tarea S2-01). Cada rechazo queda en {@code auditoria} (tarea S2-04).
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfiguration {
    private static final String TORNEOS = "/api/v1/torneos/**";
    private static final String LISTA_BUENA_FE = "/api/v1/torneos/inscripciones/*/lista-buena-fe/**";
    private static final String[] MESA_DE_CONTROL = {
            "/api/v1/torneos/partidos/*/planilla/**",
            "/api/v1/torneos/partidos/*/eventos/**",
            "/api/v1/torneos/partidos/*/cerrar"};
    private static final String ORGANIZA = Roles.ORGANIZADOR;
    private static final String ADMINISTRA = Roles.ADMIN;

    private final RegistroAccesoService registro;

    public SeguridadConfiguration(RegistroAccesoService registro) {
        this.registro = registro;
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, JwtAutenticacionFilter jwt) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sesion ->
                        sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Los roles los asigna el administrador y nadie más.
                        .requestMatchers("/api/v1/roles", "/api/v1/usuarios/*/roles")
                        .hasRole(ADMINISTRA)
                        .requestMatchers(HttpMethod.GET, TORNEOS).authenticated()
                        .requestMatchers(LISTA_BUENA_FE)
                        .hasAnyRole(Roles.ENTRENADOR, ORGANIZA, ADMINISTRA)
                        .requestMatchers(HttpMethod.POST, "/api/v1/torneos/inscripciones")
                        .hasAnyRole(Roles.ENTRENADOR, ORGANIZA, ADMINISTRA)
                        // La planilla, los eventos y el cierre los carga la mesa de control
                        // (HU 9 y 10); qué partido puede tocar lo controla AccesoTorneos.
                        .requestMatchers(MESA_DE_CONTROL)
                        .hasAnyRole(Roles.MESA_CONTROL, ORGANIZA, ADMINISTRA)
                        .requestMatchers(TORNEOS).hasAnyRole(ORGANIZA, ADMINISTRA)
                        .anyRequest().permitAll())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(this::sinSesion)
                        .accessDeniedHandler(sinPermiso()))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 401: no vino token, o no servía. */
    private void sinSesion(HttpServletRequest request, HttpServletResponse response,
            org.springframework.security.core.AuthenticationException excepcion) throws IOException {
        registrar(request, null, "Sin token válido");
        responder(request, response, HttpStatus.UNAUTHORIZED,
                "Hace falta iniciar sesión: mandá el token en Authorization: Bearer");
    }

    /** 403: el token es válido pero al usuario le falta el rol. */
    private AccessDeniedHandler sinPermiso() {
        return (request, response, excepcion) -> {
            registrar(request, usuarioActual(), "Sin permiso para " + request.getRequestURI());
            responder(request, response, HttpStatus.FORBIDDEN,
                    "Tu rol no tiene permiso para esta operación");
        };
    }

    private Long usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario.usuarioId();
        }
        return null;
    }

    private void registrar(HttpServletRequest request, Long usuarioId, String motivo) {
        registro.rechazado(request.getMethod(), request.getRequestURI(), usuarioId, motivo);
    }

    /** La misma forma que usa ApiExceptionHandler: un ProblemDetail con detail. */
    private void responder(HttpServletRequest request, HttpServletResponse response,
            HttpStatus estado, String detalle) throws IOException {
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,"
                + "\"detail\":\"%s\",\"instance\":\"%s\"}")
                .formatted(estado.getReasonPhrase(), estado.value(), detalle,
                        request.getRequestURI()));
    }
}
