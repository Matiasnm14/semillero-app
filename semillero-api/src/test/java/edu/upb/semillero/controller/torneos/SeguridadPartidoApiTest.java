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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 16 aplicada al sprint 3: la mesa de control llena la planilla, carga los
 * eventos y cierra, pero solo en el partido que tiene asignado (tarea S2-03); no
 * reprograma ni reabre, que son del organizador. Con token de verdad, como la
 * web y la app.
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
    void laMesaDeControlSoloManejaElPartidoQueTieneAsignado() throws Exception {
        Usuario mesa = usuarioCon(Roles.MESA_CONTROL);
        Usuario otraMesa = usuarioCon(Roles.MESA_CONTROL);
        Usuario consulta = usuarioCon(Roles.CONSULTA);
        Partido partido = partidoConMesa(mesa);
        String tokenMesa = token(mesa);
        String tokenOtraMesa = token(otraMesa);
        String tokenConsulta = token(consulta);
        String url = "/api/v1/torneos/partidos/" + partido.id();

        con(tokenOtraMesa, put(url + "/planilla"), partido.planilla(otraMesa.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("no está asignado")));
        con(tokenConsulta, put(url + "/planilla"), partido.planilla(consulta.getId()))
                .andExpect(status().isForbidden());

        con(tokenMesa, put(url + "/planilla"), partido.planilla(mesa.getId()))
                .andExpect(status().isOk());
        mvc.perform(post(url + "/planilla/confirmar").param("usuarioId", String.valueOf(mesa.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenMesa))
                .andExpect(status().isOk());
        con(tokenMesa, post(url + "/eventos"), Map.of("tipo", "GOL", "minuto", 10,
                        "jugadorId", partido.titularLocal(), "tipoGol", "JUGADA",
                        "usuarioId", mesa.getId()))
                .andExpect(status().isCreated());
        con(tokenOtraMesa, post(url + "/cerrar"), Map.of("usuarioId", otraMesa.getId()))
                .andExpect(status().isForbidden());
        con(tokenMesa, post(url + "/cerrar"), Map.of("usuarioId", mesa.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerradoPorId").value(mesa.getId()));

        // Reabrir y reprogramar son del organizador.
        con(tokenMesa, post(url + "/reabrir"), Map.of("motivo", "Error", "usuarioId", mesa.getId()))
                .andExpect(status().isForbidden());
        con(tokenMesa, post(url + "/reprogramaciones"), Map.of(
                        "fechaInicio", LocalDateTime.now().plusDays(60).withNano(0).toString(),
                        "canchaId", 1, "motivo", "Lluvia", "usuarioId", mesa.getId()))
                .andExpect(status().isForbidden());

        // Cualquiera con sesión lee la planilla y la tabla.
        mvc.perform(get(url + "/planilla").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenConsulta))
                .andExpect(status().isOk());
        mvc.perform(get(partido.tabla()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenConsulta))
                .andExpect(status().isOk());
    }

    /** Arma el torneo como organizador y le asigna la mesa al primer partido. */
    private Partido partidoConMesa(Usuario mesa) throws Exception {
        TestSecurityContextHolder.setAuthentication(new UsernamePasswordAuthenticationToken(
                "organizador", null, List.of(new SimpleGrantedAuthority("ROLE_" + Roles.ORGANIZADOR))));
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long id = partido.get("id").asLong();
        Map<String, Object> ajuste = new LinkedHashMap<>();
        ajuste.put("fechaInicio", partido.get("fechaInicio").asText());
        ajuste.put("mesaControlId", mesa.getId());
        ajuste.put("usuarioId", torneo.usuarioId);
        torneo.putJson(torneo.partido(id), ajuste).andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaControlId").value(mesa.getId()));
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

    private Usuario usuarioCon(String codigoRol) {
        Usuario usuario = new Usuario();
        usuario.setName("u" + System.nanoTime());
        usuario.setNombres("Mesa");
        usuario.setApellidos("Control");
        usuario.setEmail("mesa" + System.nanoTime() + "@example.com");
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
