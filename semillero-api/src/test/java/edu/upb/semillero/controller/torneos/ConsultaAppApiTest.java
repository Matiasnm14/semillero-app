package edu.upb.semillero.controller.torneos;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 14 y HU 15 - Lo que consultan la app, la web y el microservicio de video:
 * tareas S4-07 (endpoints), S4-14 (próximos partidos), S4-15 (aviso de
 * suspensión) y S4-16 (notificaciones).
 *
 * <p>Con {@code @WithMockUser} no hay token, así que "mi cuenta" y las
 * notificaciones van con {@code usuarioId}; con token, el dueño sale del token
 * ({@code SeguridadSprint4ApiTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class ConsultaAppApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** S4-14: los próximos partidos del jugador, con fecha, hora, cancha y superficie. */
    @Test
    void losProximosPartidosTraenLaCanchaYSuSuperficie() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 3, 2);
        JsonNode fixture = torneo.generarLiga();
        EscenarioTorneo.Equipo equipo = torneo.equipos.get(0);
        long jugador = equipo.jugadores().get(0);
        String proximosDelJugador = EscenarioTorneo.TORNEOS + "/jugadores/" + jugador + "/proximos-partidos";
        String proximosDeLaCategoria = EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/categorias/"
                + torneo.categoriaId + "/proximos-partidos";

        JsonNode suyos = leer(get(proximosDelJugador));
        assertThat(suyos).hasSize(2);
        for (JsonNode partido : suyos) {
            assertThat(partido.get("clubLocalId").asLong() == equipo.clubId()
                    || partido.get("clubVisitanteId").asLong() == equipo.clubId()).isTrue();
            assertThat(partido.get("canchaSuperficie").asText()).isEqualTo("CESPED_SINTETICO");
            assertThat(partido.get("fechaInicio").isNull()).isFalse();
        }
        assertThat(suyos.get(0).get("fechaInicio").asText())
                .isLessThan(suyos.get(1).get("fechaInicio").asText());
        mvc.perform(get(proximosDeLaCategoria)).andExpect(jsonPath("$.length()").value(3));

        // Un partido cerrado ya no es próximo.
        JsonNode primero = torneo.partidoDe(fixture, equipo.clubId(), 0);
        torneo.cerrar(primero.get("id").asLong(), Map.of("ganadorWalkoverId", equipo.clubId()))
                .andExpect(status().isOk());
        mvc.perform(get(proximosDelJugador)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(proximosDeLaCategoria)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/categorias/" + Long.MAX_VALUE
                + "/proximos-partidos")).andExpect(status().isNotFound());
    }

    /**
     * S4-15 y S4-16: el jugador con cuenta ve en "mi cuenta" que está
     * suspendido y tiene el aviso; el entrenador que inscribió recibe el
     * resultado publicado. Los avisos se marcan como leídos.
     */
    @Test
    void miCuentaAvisaLaSuspensionYLasNotificacionesSeLeen() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long usuarioJugador = torneo.usuario("jugador");
        long jugador = torneo.jugadorConCuenta(torneo.equipos.get(0), usuarioJugador, 9);
        String miCuenta = EscenarioTorneo.TORNEOS + "/mi-cuenta";
        String notificaciones = EscenarioTorneo.TORNEOS + "/notificaciones";

        mvc.perform(get(miCuenta).param("usuarioId", String.valueOf(usuarioJugador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jugadorIds[0]").value(jugador))
                .andExpect(jsonPath("$.suspendido").value(false))
                .andExpect(jsonPath("$.roles[0]").value("CONSULTA"))
                .andExpect(jsonPath("$.proximosPartidos.length()").value(1))
                .andExpect(jsonPath("$.notificacionesSinLeer").value(0));

        torneo.postJson(EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/sanciones", Map.of(
                        "jugadorId", jugador, "fechasSuspension", 2, "motivo", "Agresión a un rival",
                        "usuarioId", torneo.usuarioId))
                .andExpect(status().isCreated());

        mvc.perform(get(miCuenta).param("usuarioId", String.valueOf(usuarioJugador)))
                .andExpect(jsonPath("$.suspendido").value(true))
                .andExpect(jsonPath("$.suspensionesVigentes[0].fechasPendientes").value(2))
                .andExpect(jsonPath("$.notificacionesSinLeer").value(1));
        long avisoId = leer(get(notificaciones).param("usuarioId", String.valueOf(usuarioJugador)))
                .get(0).get("id").asLong();
        mvc.perform(get(notificaciones).param("usuarioId", String.valueOf(usuarioJugador)))
                .andExpect(jsonPath("$[0].titulo").value("Suspensión"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("2 fecha(s)")))
                .andExpect(jsonPath("$[0].leida").value(false));

        mvc.perform(put(notificaciones + "/" + avisoId + "/leida")
                        .param("usuarioId", String.valueOf(torneo.usuarioId)))
                .andExpect(status().isNotFound());
        mvc.perform(put(notificaciones + "/" + avisoId + "/leida")
                        .param("usuarioId", String.valueOf(usuarioJugador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
        mvc.perform(get(notificaciones).param("usuarioId", String.valueOf(usuarioJugador))
                        .param("soloNoLeidas", "true"))
                .andExpect(jsonPath("$.length()").value(0));

        // El resultado publicado le llega a los equipos al cerrar.
        torneo.cerrar(partido.get("id").asLong(),
                        Map.of("ganadorWalkoverId", partido.get("clubLocalId").asLong()))
                .andExpect(status().isOk());
        mvc.perform(get(notificaciones).param("usuarioId", String.valueOf(torneo.usuarioId))
                        .param("soloNoLeidas", "true"))
                .andExpect(jsonPath("$[0].titulo").value("Resultado publicado"))
                .andExpect(jsonPath("$[0].mensaje").value(containsString("3 - 0")));
        mvc.perform(get(notificaciones).param("usuarioId", String.valueOf(usuarioJugador))
                        .param("soloNoLeidas", "true"))
                .andExpect(jsonPath("$[0].titulo").value("Resultado publicado"));
        mvc.perform(put(notificaciones + "/leidas").param("usuarioId", String.valueOf(usuarioJugador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(1));

        // Sin token y sin usuarioId no se sabe de quién son.
        mvc.perform(get(notificaciones)).andExpect(status().isBadRequest());
    }

    /** HU 15: un partido por su ID y los cerrados por fecha de cierre, para el microservicio de video. */
    @Test
    void elPartidoYLosCerradosSeConsultanPorLaApi() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long partidoId = partido.get("id").asLong();
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        mvc.perform(get(torneo.partido(partidoId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(partidoId))
                .andExpect(jsonPath("$.canchaSuperficie").value("CESPED_SINTETICO"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"));
        mvc.perform(get(torneo.partido(Long.MAX_VALUE))).andExpect(status().isNotFound());

        torneo.cerrar(partidoId, Map.of("ganadorWalkoverId", partido.get("clubVisitanteId").asLong()))
                .andExpect(status().isOk());
        JsonNode cerrados = leer(get(EscenarioTorneo.TORNEOS + "/partidos/finalizados")
                .param("desde", antes.toString()));
        boolean esta = false;
        for (JsonNode cerrado : cerrados) {
            if (cerrado.get("partidoId").asLong() == partidoId) {
                esta = true;
                assertThat(cerrado.get("fechaCierre").isNull()).isFalse();
                assertThat(cerrado.get("torneoCategoriaId").asLong()).isEqualTo(torneo.categoriaId);
            }
        }
        assertThat(esta).isTrue();
        mvc.perform(get(EscenarioTorneo.TORNEOS + "/partidos/finalizados")
                        .param("desde", LocalDateTime.now().plusDays(1).toString()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private JsonNode leer(org.springframework.test.web.servlet.RequestBuilder pedido) throws Exception {
        return json.readTree(mvc.perform(pedido).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }
}
