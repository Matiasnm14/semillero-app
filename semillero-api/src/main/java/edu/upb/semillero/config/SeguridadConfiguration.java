package edu.upb.semillero.config;

import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.seguridad.RegistroAccesoService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
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
 *   <li>todo lo demás que escribe (torneos, categorías, reglamento, sedes,
 *       canchas, estados de inscripción y fixture): ORGANIZADOR o ADMIN.</li>
 * </ul>
 *
 * <p>Las cuentas solo las modifica su titular o un administrador. Las
 * asignaciones de administradores y vínculos con clubes requieren ADMIN porque
 * determinan los roles y la propiedad que usa torneos. El registro de usuarios
 * y el resto de las rutas compartidas conservan su acceso público.
 *
 * <p>Sin sesión de servidor y sin CSRF porque la API es stateless y se usa con
 * token (tarea S2-01). Cada rechazo queda en {@code auditoria} (tarea S2-04).
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfiguration {
    private static final String TORNEOS = "/api/v1/torneos/**";
    private static final String LISTA_BUENA_FE = "/api/v1/torneos/inscripciones/*/lista-buena-fe/**";
    private static final String ORGANIZA = Roles.ORGANIZADOR;
    private static final String ADMINISTRA = Roles.ADMIN;

    private final RegistroAccesoService registro;
    private final JugadorRepository jugadores;
    private final FichadoRepository fichados;
    private final UsuarioClubRepository usuariosClub;

    public SeguridadConfiguration(RegistroAccesoService registro, JugadorRepository jugadores,
            FichadoRepository fichados, UsuarioClubRepository usuariosClub) {
        this.registro = registro;
        this.jugadores = jugadores;
        this.fichados = fichados;
        this.usuariosClub = usuariosClub;
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, JwtAutenticacionFilter jwt) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sesion ->
                        sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Los administradores solo los asigna otro administrador.
                        .requestMatchers("/api/v1/roles", "/api/v1/usuarios/*/roles")
                        .hasRole(ADMINISTRA)
                        .requestMatchers("/api/v1/administradores", "/api/v1/administradores/**")
                        .hasRole(ADMINISTRA)
                        // Una cuenta de club puede registrarse sin sesión. Solo
                        // el alta inicial es pública; cambios y bajas siguen
                        // requiriendo ADMIN.
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios-club",
                                "/api/v1/usuarios-trabaja")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios-club/**",
                                "/api/v1/usuarios-trabaja/**").permitAll()
                        .requestMatchers("/api/v1/usuarios-club", "/api/v1/usuarios-club/**",
                                "/api/v1/usuarios-trabaja", "/api/v1/usuarios-trabaja/**")
                        .hasRole(ADMINISTRA)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/{id}")
                        .access(this::usuarioPropioOClubOAdministrador)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/{id}")
                        .access(this::usuarioPropioOAdministrador)
                        // Un jugador puede modificar su ficha. El personal de club
                        // únicamente puede modificar fichas de jugadores fichados
                        // actualmente por su propio club.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/jugadores/{id}")
                        .access(this::jugadorPropioOClubOAdministrador)
                        .requestMatchers(HttpMethod.GET, TORNEOS).authenticated()
                        .requestMatchers(LISTA_BUENA_FE)
                        .hasAnyRole(Roles.ENTRENADOR, ORGANIZA, ADMINISTRA)
                        .requestMatchers(HttpMethod.POST, "/api/v1/torneos/inscripciones")
                        .hasAnyRole(Roles.ENTRENADOR, ORGANIZA, ADMINISTRA)
                        .requestMatchers(TORNEOS).hasAnyRole(ORGANIZA, ADMINISTRA)
                        .anyRequest().permitAll())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(this::sinSesion)
                        .accessDeniedHandler(sinPermiso()))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private AuthorizationDecision usuarioPropioOAdministrador(
            Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) {
        Authentication actual = autenticacion.get();
        boolean permitido = actual != null && actual.isAuthenticated()
                && (actual.getAuthorities().stream()
                        .anyMatch(rol -> ("ROLE_" + ADMINISTRA).equals(rol.getAuthority()))
                    || actual.getPrincipal() instanceof UsuarioAutenticado usuario
                        && usuario.usuarioId() != null
                        && usuario.usuarioId().toString().equals(contexto.getVariables().get("id")));
        return new AuthorizationDecision(permitido);
    }

    /**
     * Permite actualizar una cuenta propia o, si la cuenta corresponde a un
     * jugador, al personal del club que lo tiene fichado actualmente.
     */
    private AuthorizationDecision usuarioPropioOClubOAdministrador(
            Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) {
        Authentication actual = autenticacion.get();
        if (actual == null || !actual.isAuthenticated()
                || !(actual.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return new AuthorizationDecision(false);
        }
        if (esAdministrador(actual)) {
            return new AuthorizationDecision(true);
        }
        Long usuarioObjetivo;
        try {
            usuarioObjetivo = Long.valueOf(contexto.getVariables().get("id"));
        } catch (NumberFormatException | NullPointerException ex) {
            return new AuthorizationDecision(false);
        }
        if (usuarioObjetivo.equals(usuario.usuarioId())) {
            return new AuthorizationDecision(true);
        }
        boolean esJugadorDelClub = usuario.clubId() != null
                && usuariosClub.findFirstByUsuarioId(usuario.usuarioId()).isPresent()
                && jugadores.findFirstByUsuarioId(usuarioObjetivo)
                        .map(jugador -> fichados
                                .existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(
                                        jugador.getId(), usuario.clubId()))
                        .orElse(false);
        return new AuthorizationDecision(esJugadorDelClub);
    }

    private AuthorizationDecision jugadorPropioOClubOAdministrador(
            Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) {
        Authentication actual = autenticacion.get();
        if (actual == null || !actual.isAuthenticated()
                || !(actual.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return new AuthorizationDecision(false);
        }
        if (esAdministrador(actual)) {
            return new AuthorizationDecision(true);
        }
        Long jugadorId;
        try {
            jugadorId = Long.valueOf(contexto.getVariables().get("id"));
        } catch (NumberFormatException | NullPointerException ex) {
            return new AuthorizationDecision(false);
        }
        boolean esPropio = jugadores.findFirstByUsuarioId(usuario.usuarioId())
                .map(jugador -> jugadorId.equals(jugador.getId()))
                .orElse(false);
        boolean esJugadorDelClub = usuario.clubId() != null
                && usuariosClub.findFirstByUsuarioId(usuario.usuarioId()).isPresent()
                && fichados.existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(
                        jugadorId, usuario.clubId());
        return new AuthorizationDecision(esPropio || esJugadorDelClub);
    }

    private boolean esAdministrador(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream()
                .anyMatch(rol -> ("ROLE_" + ADMINISTRA).equals(rol.getAuthority()));
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
