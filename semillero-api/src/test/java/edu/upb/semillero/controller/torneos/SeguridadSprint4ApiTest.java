package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.Administrador;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioRol;
import edu.upb.semillero.repository.repos.AdministradorRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.RolRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.service.comun.Roles;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 15 - Tarea S4-10, seguridad de la API, con los roles unificados del login
 * común: el error dice si falta el token o si no sirve, cada uno ve solo sus
 * avisos (salvo el administrador), un usuario del sistema escribe y uno de solo
 * lectura no, y las consultas se leen con cualquier sesión.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadSprint4ApiTest {
    private static final String TORNEOS = "/api/v1/torneos";
    private static final String CLAVE = "secreto123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository usuariosRoles;
    @Autowired AdministradorRepository administradores;
    @Autowired JugadorRepository jugadores;
    @Autowired PasswordEncoder passwordEncoder;

    /** El 401 dice si hay que iniciar sesión o si el token no sirve. */
    @Test
    void elErrorDiceSiFaltaElTokenOSiNoSirve() throws Exception {
        mvc.perform(get(TORNEOS + "/mi-cuenta")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("iniciar sesión")));
        mvc.perform(get(TORNEOS + "/mi-cuenta").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("no es válido o ya venció")));
    }

    /** Cada uno ve sus avisos y su cuenta; pedir los de otro da 403, salvo al administrador. */
    @Test
    void cadaUnoVeSoloSusNotificaciones() throws Exception {
        Usuario uno = usuarioSistema();
        Usuario otro = usuarioSistema();
        String token = token(uno);

        con(token, get(TORNEOS + "/notificaciones")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        con(token, get(TORNEOS + "/mi-cuenta")).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(uno.getId()))
                .andExpect(jsonPath("$.suspendido").value(false));
        con(token, get(TORNEOS + "/notificaciones").param("usuarioId", String.valueOf(otro.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("tus propios datos")));
        con(token, put(TORNEOS + "/notificaciones/leidas")).andExpect(status().isOk());

        String admin = token(usuarioAdmin());
        con(admin, get(TORNEOS + "/mi-cuenta").param("usuarioId", String.valueOf(otro.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(otro.getId()));
    }

    /** El usuario del sistema escribe sanciones y apelaciones; el de solo lectura (jugador) no. */
    @Test
    void elUsuarioSistemaEscribeYElDeSoloLecturaNo() throws Exception {
        String sistema = token(usuarioSistema());
        String jugador = token(usuarioJugador());
        Map<String, Object> apelacion = Map.of("motivo", "No fue roja", "usuarioId", 1);
        Map<String, Object> resolucion = Map.of("estado", "ACEPTADA", "resolucion", "Ok", "usuarioId", 1);
        Map<String, Object> sancion = Map.of("jugadorId", 1, "fechasSuspension", 1, "motivo", "x", "usuarioId", 1);

        // El jugador (solo lectura) no puede escribir: 403 por rol.
        con(jugador, post(TORNEOS + "/sanciones/999999/apelaciones"), apelacion)
                .andExpect(status().isForbidden());
        con(jugador, put(TORNEOS + "/apelaciones/999999/resolucion"), resolucion)
                .andExpect(status().isForbidden());
        con(jugador, post(TORNEOS + "/999999/sanciones"), sancion).andExpect(status().isForbidden());

        // El usuario del sistema sí: el rol lo deja pasar y llega al servicio (404, no existe).
        con(sistema, post(TORNEOS + "/sanciones/999999/apelaciones"), apelacion)
                .andExpect(status().isNotFound());
        con(sistema, put(TORNEOS + "/apelaciones/999999/resolucion"), resolucion)
                .andExpect(status().isNotFound());
        con(sistema, post(TORNEOS + "/999999/sanciones"), sancion).andExpect(status().isNotFound());
    }

    /** Las estadísticas, las sanciones y los partidos cerrados se leen con cualquier rol. */
    @Test
    void laConsultaLeeLasEstadisticasYLasSanciones() throws Exception {
        String sesion = token(usuarioJugador());

        con(sesion, get(TORNEOS + "/999999/categorias/999999/estadisticas"))
                .andExpect(status().isNotFound());
        con(sesion, get(TORNEOS + "/999999/sanciones")).andExpect(status().isNotFound());
        con(sesion, get(TORNEOS + "/jugadores/999999/sanciones")).andExpect(status().isOk());
        con(sesion, get(TORNEOS + "/partidos/finalizados")).andExpect(status().isOk());
        mvc.perform(get(TORNEOS + "/partidos/finalizados")).andExpect(status().isUnauthorized());
    }

    private ResultActions con(String token, MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions con(String token, MockHttpServletRequestBuilder pedido, Map<String, Object> body)
            throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private Usuario nuevoUsuario() {
        Usuario usuario = new Usuario();
        usuario.setName("s4" + System.nanoTime());
        usuario.setNombres("Prueba");
        usuario.setApellidos("Sprint4");
        usuario.setEmail("s4" + System.nanoTime() + "@example.com");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        return usuarios.saveAndFlush(usuario);
    }

    private Usuario usuarioSistema() {
        Usuario usuario = nuevoUsuario();
        Rol rol = roles.findByCodigo(Roles.USUARIO_SISTEMA).orElseThrow();
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setUsuario(usuario);
        asignacion.setRol(rol);
        asignacion.setFechaAlta(LocalDateTime.now());
        usuariosRoles.saveAndFlush(asignacion);
        return usuario;
    }

    private Usuario usuarioAdmin() {
        Usuario usuario = nuevoUsuario();
        Administrador administrador = new Administrador();
        administrador.setUsuario(usuario);
        administradores.saveAndFlush(administrador);
        return usuario;
    }

    private Usuario usuarioJugador() {
        Usuario usuario = nuevoUsuario();
        Jugador jugador = new Jugador();
        jugador.setUsuario(usuario);
        jugadores.saveAndFlush(jugador);
        return usuario;
    }

    private String token(Usuario usuario) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identificador", usuario.getEmail(),
                                "password", CLAVE))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asString();
    }
}
