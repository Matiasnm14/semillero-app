package edu.upb.semillero.controller.torneos;

import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tarea S3-21 - El flujo completo de un partido, de punta a punta por la API:
 * fixture generado, planilla precargada, guardada y confirmada (HU 9), eventos
 * (HU 10), cierre con goles y minutos (S3-08 y S3-09), tabla de posiciones
 * (HU 11), reapertura (S3-10) y reprogramación de otro partido (HU 8).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class FlujoCompletoPartidoApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void unPartidoDeLaPlanillaALaTabla() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 3, 4);
        JsonNode fixture = torneo.generarLiga();
        JsonNode primero = fixture.get(0);
        long partidoId = primero.get("id").asLong();
        long local = primero.get("clubLocalId").asLong();
        long visitante = primero.get("clubVisitanteId").asLong();
        long tercero = torneo.equipos.stream().mapToLong(EscenarioTorneo.Equipo::clubId)
                .filter(club -> club != local && club != visitante).findFirst().orElseThrow();
        List<Long> l = torneo.equipoDe(local).jugadores();
        List<Long> v = torneo.equipoDe(visitante).jugadores();
        String partido = torneo.partido(partidoId);

        // HU 9: la planilla viene precargada aunque nadie la haya guardado.
        mvc.perform(get(partido + "/planilla")).andExpect(status().isOk())
                .andExpect(jsonPath("$.planillaId").doesNotExist())
                .andExpect(jsonPath("$.canchaSuperficie").value("CESPED_SINTETICO"))
                .andExpect(jsonPath("$.torneoNombre").value("Copa Sprint 3"))
                .andExpect(jsonPath("$.local.clubId").value(local))
                .andExpect(jsonPath("$.local.jugadores.length()").value(4))
                .andExpect(jsonPath("$.local.jugadores[0].bloqueado").value(false))
                .andExpect(jsonPath("$.local.jugadores[0].enPlanilla").value(false));

        torneo.planillaCompleta(partidoId, local, visitante, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.local.titulares").value(3))
                .andExpect(jsonPath("$.local.suplentes").value(1))
                .andExpect(jsonPath("$.local.cuerpoTecnico[0].cargo").value("DT"));
        torneo.confirmarPlanilla(partidoId);

        // HU 10: goles, tarjetas y cambios.
        torneo.evento(partidoId, "GOL", 10, l.get(0), l.get(1), "JUGADA", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clubId").value(local));
        torneo.evento(partidoId, "AMARILLA", 20, v.get(0), null, null, "Juego brusco")
                .andExpect(status().isCreated());
        torneo.evento(partidoId, "CAMBIO", 50, l.get(2), l.get(3), null, null)
                .andExpect(status().isCreated());
        torneo.evento(partidoId, "ROJA", 60, v.get(1), null, null, "Agresión")
                .andExpect(status().isCreated());
        torneo.evento(partidoId, "GOL", 65, v.get(2), null, "AUTOGOL", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clubId").value(local));
        mvc.perform(get(partido + "/eventos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get(partido + "/resultado"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));

        // S3-08 y S3-09: el cierre guarda goles y minutos.
        JsonNode cerrado = json.readTree(torneo.cerrar(partidoId, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.golesLocal").value(2))
                .andExpect(jsonPath("$.golesVisitante").value(0))
                .andExpect(jsonPath("$.fechaCierre").exists())
                .andExpect(jsonPath("$.cerradoPorId").value(torneo.usuarioId))
                .andReturn().getResponse().getContentAsString());
        assertThat(minutos(cerrado, l.get(0))).isEqualTo(70);
        assertThat(minutos(cerrado, l.get(2))).isEqualTo(50);
        assertThat(minutos(cerrado, l.get(3))).isEqualTo(20);
        assertThat(minutos(cerrado, v.get(1))).isEqualTo(60);
        assertThat(minutos(cerrado, v.get(3))).isZero();
        assertThat(jugador(cerrado, v.get(3)).get("minutoSalida").asInt()).isZero();
        assertThat(jugador(cerrado, v.get(1)).get("minutoSalida").asInt()).isEqualTo(60);

        // HU 11: la tabla sale de los partidos cerrados.
        mvc.perform(get(torneo.tabla())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partidosCerrados").value(1))
                .andExpect(jsonPath("$[0].filas[0].clubId").value(local))
                .andExpect(jsonPath("$[0].filas[0].puntos").value(3))
                .andExpect(jsonPath("$[0].filas[0].golesFavor").value(2))
                .andExpect(jsonPath("$[0].filas[1].clubId").value(tercero))
                .andExpect(jsonPath("$[0].filas[1].partidosJugados").value(0))
                .andExpect(jsonPath("$[0].filas[2].clubId").value(visitante))
                .andExpect(jsonPath("$[0].filas[2].diferenciaGoles").value(-2))
                .andExpect(jsonPath("$[0].filas[2].fairPlay").value(4));

        // S3-04: la planilla cerrada se exporta con los minutos.
        byte[] pdf = mvc.perform(get(partido + "/planilla/pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF");

        // S3-10: el organizador lo reabre, la tabla lo deja de contar y se cierra de nuevo.
        torneo.postJson(partido + "/reabrir", Map.of("motivo", "Gol mal cargado",
                        "usuarioId", torneo.usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.golesLocal").doesNotExist());
        mvc.perform(get(torneo.tabla()))
                .andExpect(jsonPath("$[0].partidosCerrados").value(0));
        torneo.cerrar(partidoId, Map.of()).andExpect(status().isOk());
        mvc.perform(get(partido + "/historial")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].accion").value("REABRIR"))
                .andExpect(jsonPath("$[1].motivo").value("Gol mal cargado"));

        // HU 8: otro partido se reprograma y queda en el historial.
        JsonNode otro = fixture.get(1);
        torneo.postJson(torneo.partido(otro.get("id").asLong()) + "/reprogramaciones", Map.of(
                        "fechaInicio", torneo.primeraJornada.plusDays(2).atTime(16, 0).toString(),
                        "canchaId", torneo.canchaB,
                        "motivo", "Lluvia",
                        "usuarioId", torneo.usuarioId))
                .andExpect(status().isCreated());
        mvc.perform(get(torneo.partido(otro.get("id").asLong()) + "/reprogramaciones"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].motivo").value("Lluvia"));
    }

    private JsonNode jugador(JsonNode resultado, long jugadorId) {
        for (JsonNode jugador : resultado.get("jugadores")) {
            if (jugador.get("jugadorId").asLong() == jugadorId) {
                return jugador;
            }
        }
        throw new AssertionError("El jugador " + jugadorId + " no está en el resultado");
    }

    private int minutos(JsonNode resultado, long jugadorId) {
        return jugador(resultado, jugadorId).get("minutosJugados").asInt();
    }
}
