package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioRol;
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
 * HU 15 - Tarea S4-10, seguridad de la API, sobre lo que agregó el sprint 4:
 * el error dice si falta el token o si no sirve, cada uno ve solo sus avisos,
 * el entrenador apela pero solo el organizador resuelve, y la consulta lee.
 * Con token de verdad, como la web y la app.
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
        Usuario uno = usuarioCon(Roles.CONSULTA);
        Usuario otro = usuarioCon(Roles.CONSULTA);
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

        String admin = token(usuarioCon(Roles.ADMIN));
        con(admin, get(TORNEOS + "/mi-cuenta").param("usuarioId", String.valueOf(otro.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(otro.getId()));
    }

    /** Apela el entrenador (y el organizador); resuelve solo el organizador. */
    @Test
    void elEntrenadorApelaYElOrganizadorResuelve() throws Exception {
        String consulta = token(usuarioCon(Roles.CONSULTA));
        String entrenador = token(usuarioCon(Roles.ENTRENADOR));
        String mesa = token(usuarioCon(Roles.MESA_CONTROL));
        Map<String, Object> apelacion = Map.of("motivo", "No fue roja", "usuarioId", 1);
        Map<String, Object> resolucion = Map.of("estado", "ACEPTADA", "resolucion", "Ok", "usuarioId", 1);

        con(consulta, post(TORNEOS + "/sanciones/999999/apelaciones"), apelacion)
                .andExpect(status().isForbidden());
        // El rol lo deja pasar: llega al servicio, que no encuentra la sanción.
        con(entrenador, post(TORNEOS + "/sanciones/999999/apelaciones"), apelacion)
                .andExpect(status().isNotFound());
        con(entrenador, put(TORNEOS + "/apelaciones/999999/resolucion"), resolucion)
                .andExpect(status().isForbidden());
        con(mesa, put(TORNEOS + "/apelaciones/999999/resolucion"), resolucion)
                .andExpect(status().isForbidden());
        con(entrenador, post(TORNEOS + "/999999/sanciones"), Map.of("jugadorId", 1,
                        "fechasSuspension", 1, "motivo", "x", "usuarioId", 1))
                .andExpect(status().isForbidden());
    }

    /** Las estadísticas, las sanciones y los partidos cerrados se leen con cualquier rol. */
    @Test
    void laConsultaLeeLasEstadisticasYLasSanciones() throws Exception {
        String consulta = token(usuarioCon(Roles.CONSULTA));

        con(consulta, get(TORNEOS + "/999999/categorias/999999/estadisticas"))
                .andExpect(status().isNotFound());
        con(consulta, get(TORNEOS + "/999999/sanciones")).andExpect(status().isNotFound());
        con(consulta, get(TORNEOS + "/jugadores/999999/sanciones")).andExpect(status().isOk());
        con(consulta, get(TORNEOS + "/partidos/finalizados")).andExpect(status().isOk());
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

    private Usuario usuarioCon(String codigoRol) {
        Usuario usuario = new Usuario();
        usuario.setName("s4" + System.nanoTime());
        usuario.setNombres("Prueba");
        usuario.setApellidos("Sprint4");
        usuario.setEmail("s4" + System.nanoTime() + "@example.com");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario = usuarios.saveAndFlush(usuario);
        Rol rol = roles.findByCodigo(codigoRol).orElseThrow();
        UsuarioRol asignacion = new UsuarioRol();
        asignacion.setUsuario(usuario);
        asignacion.setRol(rol);
        asignacion.setFechaAlta(LocalDateTime.now());
        usuariosRoles.saveAndFlush(asignacion);
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
