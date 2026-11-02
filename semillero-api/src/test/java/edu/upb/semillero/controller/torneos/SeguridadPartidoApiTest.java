package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioRol;
import edu.upb.semillero.repository.repos.RolRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.service.comun.Roles;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 16 aplicada al sprint 3, con los roles unificados del login común: un
 * {@code USUARIO_SISTEMA} llena la planilla, carga los eventos, cierra y reabre
 * el partido; sin token no se puede escribir y cualquiera con sesión lee. Con
 * token de verdad, como la web y la app.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadPartidoApiTest {
    private static final String CLAVE = "secreto123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired UsuarioRolRepository usuariosRoles;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiar() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void unUsuarioSistemaOperaElPartidoYSinTokenNoSePuede() throws Exception {
        Usuario sistema = usuarioSistema();
        Partido partido = armarPartido();
        String token = token(sistema);
        String url = "/api/v1/torneos/partidos/" + partido.id();

        // Sin token no se puede escribir la planilla.
        mvc.perform(put(url + "/planilla").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(partido.planilla(sistema.getId()))))
                .andExpect(status().isUnauthorized());

        // El usuario del sistema llena la planilla, confirma, carga un gol y cierra.
        con(token, put(url + "/planilla"), partido.planilla(sistema.getId()))
                .andExpect(status().isOk());
        mvc.perform(post(url + "/planilla/confirmar").param("usuarioId", String.valueOf(sistema.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        con(token, post(url + "/eventos"), Map.of("tipo", "GOL", "minuto", 10,
                        "jugadorId", partido.titularLocal(), "tipoGol", "JUGADA",
                        "usuarioId", sistema.getId()))
                .andExpect(status().isCreated());
        con(token, post(url + "/cerrar"), Map.of("usuarioId", sistema.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerradoPorId").value(sistema.getId()));
        // Reabrir y reprogramar también los puede un usuario del sistema.
        con(token, post(url + "/reabrir"), Map.of("motivo", "Error", "usuarioId", sistema.getId()))
                .andExpect(status().isOk());

        // Cualquiera con sesión lee la planilla y la tabla.
        mvc.perform(get(url + "/planilla").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get(partido.tabla()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** Arma el torneo y devuelve su primer partido. */
    private Partido armarPartido() throws Exception {
        TestSecurityContextHolder.setAuthentication(new UsernamePasswordAuthenticationToken(
                "sistema", null, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.USUARIO_SISTEMA))));
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long id = partido.get("id").asLong();
        TestSecurityContextHolder.clearContext();
        return new Partido(id, torneo.equipoDe(partido.get("clubLocalId").asLong()).jugadores(),
                torneo.equipoDe(partido.get("clubVisitanteId").asLong()).jugadores(), torneo.tabla());
    }

    private ResultActions con(String token, MockHttpServletRequestBuilder pedido,
            Map<String, Object> body) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private Usuario usuarioSistema() {
        Usuario usuario = new Usuario();
        usuario.setName("u" + System.nanoTime());
        usuario.setNombres("Sistema");
        usuario.setApellidos("Torneos");
        usuario.setEmail("sistema" + System.nanoTime() + "@example.com");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario = usuarios.saveAndFlush(usuario);
        Rol rol = roles.findByCodigo(Roles.USUARIO_SISTEMA).orElseThrow();
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

    private record Partido(long id, List<Long> local, List<Long> visitante, String tabla) {
        long titularLocal() {
            return local.get(0);
        }

        /** Los dos jugadores de cada equipo, el primero titular y capitán. */
        Map<String, Object> planilla(long usuarioId) {
            List<Map<String, Object>> jugadores = new ArrayList<>();
            for (List<Long> equipo : List.of(local, visitante)) {
                jugadores.add(EscenarioTorneo.jugador(equipo.get(0), true, true));
                jugadores.add(EscenarioTorneo.jugador(equipo.get(1), false, false));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jugadores", jugadores);
            body.put("usuarioId", usuarioId);
            return body;
        }
    }
}
