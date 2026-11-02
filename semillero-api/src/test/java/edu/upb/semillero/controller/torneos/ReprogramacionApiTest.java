package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.repos.NotificacionRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 8 - Reprogramar un partido: tareas S3-16 (endpoint y motivo), S3-17
 * (conflictos), S3-18 (historial) y S3-19 (aviso a los equipos).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class ReprogramacionApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NotificacionRepository notificaciones;

    @Test
    void reprogramaGuardaElHistorialYAvisaALosEquipos() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long partidoId = partido.get("id").asLong();
        LocalDateTime nueva = torneo.primeraJornada.plusDays(3).atTime(17, 30);
        long avisosAntes = notificaciones.findByUsuarioIdOrderByFechaDesc(torneo.usuarioId).size();

        reprogramar(torneo, partidoId, nueva, torneo.canchaB, "Cancha inundada")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaInicioAnterior").value(partido.get("fechaInicio").asText()))
                .andExpect(jsonPath("$.canchaAnteriorId").value(partido.get("canchaId").asLong()))
                .andExpect(jsonPath("$.canchaNuevaId").value(torneo.canchaB))
                .andExpect(jsonPath("$.motivo").value("Cancha inundada"))
                .andExpect(jsonPath("$.usuarioId").value(torneo.usuarioId))
                // El que inscribió a los dos equipos recibe un solo aviso.
                .andExpect(jsonPath("$.usuariosNotificados").value(1));

        mvc.perform(get(torneo.fixture())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fechaInicio").value(nueva.toString() + ":00"))
                .andExpect(jsonPath("$[0].canchaId").value(torneo.canchaB));
        mvc.perform(get(torneo.partido(partidoId) + "/reprogramaciones")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].motivo").value("Cancha inundada"));

        var avisos = notificaciones.findByUsuarioIdOrderByFechaDesc(torneo.usuarioId);
        assertThat(avisos).hasSize((int) avisosAntes + 1);
        assertThat(avisos.get(0).getTitulo()).isEqualTo("Partido reprogramado");
        assertThat(avisos.get(0).getModulo()).isEqualTo("TORNEOS");
        assertThat(avisos.get(0).getMensaje()).contains("Cancha B").contains("Cancha inundada");
    }

    /** Tarea S3-16: motivo obligatorio, fecha hacia adelante y algo que cambie. */
    @Test
    void validaElMotivoLaFechaYQueCambieAlgo() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long partidoId = partido.get("id").asLong();
        LocalDateTime actual = LocalDateTime.parse(partido.get("fechaInicio").asText());

        reprogramar(torneo, partidoId, actual.plusDays(1), torneo.canchaA, " ")
                .andExpect(status().isBadRequest());
        reprogramar(torneo, partidoId, LocalDateTime.now().minusDays(1), torneo.canchaA, "Lluvia")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("ya pasó")));
        reprogramar(torneo, partidoId, actual, partido.get("canchaId").asLong(), "Lluvia")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("nada que reprogramar")));
        reprogramar(torneo, Long.MAX_VALUE, actual.plusDays(1), torneo.canchaA, "Lluvia")
                .andExpect(status().isNotFound());
        reprogramar(torneo, partidoId, actual.plusDays(1), Long.MAX_VALUE, "Lluvia")
                .andExpect(status().isNotFound());
        mvc.perform(get(torneo.partido(partidoId) + "/reprogramaciones"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Tarea S3-17: choque de cancha y choque de equipo, con el partido con el que choca. */
    @Test
    void rechazaLosChoquesDeCanchaYDeEquipo() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 4, 2);
        JsonNode fixture = torneo.generarLiga();
        JsonNode primero = fixture.get(0);
        JsonNode segundo = fixture.get(1);
        LocalDateTime horaDelSegundo = LocalDateTime.parse(segundo.get("fechaInicio").asText());

        // Misma cancha y misma hora que otro partido.
        reprogramar(torneo, primero.get("id").asLong(), horaDelSegundo,
                segundo.get("canchaId").asLong(), "Pedido del club")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString(
                        "ya tiene el partido " + segundo.get("id").asLong())));

        // Un partido de la fecha 2 pegado al de un equipo suyo: la cancha está libre,
        // pero no quedan los 60 minutos de descanso mínimo del reglamento.
        long equipo = primero.get("clubLocalId").asLong();
        JsonNode delMismoEquipo = null;
        for (JsonNode partido : fixture) {
            if (partido.get("jornada").asInt() == 2 && (partido.get("clubLocalId").asLong() == equipo
                    || partido.get("clubVisitanteId").asLong() == equipo)) {
                delMismoEquipo = partido;
            }
        }
        LocalDateTime finDelPrimero = LocalDateTime.parse(primero.get("fechaFin").asText());
        reprogramar(torneo, delMismoEquipo.get("id").asLong(), finDelPrimero.plusMinutes(5),
                primero.get("canchaId").asLong(), "Pedido del club")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("uno de los equipos")))
                .andExpect(jsonPath("$.detail").value(containsString("descanso mínimo")));
    }

    /** HU 8: un partido cerrado no se reprograma. */
    @Test
    void noReprogramaUnPartidoCerrado() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long partidoId = partido.get("id").asLong();
        torneo.cerrar(partidoId, Map.of("ganadorWalkoverId", partido.get("clubLocalId").asLong()))
                .andExpect(status().isOk());

        reprogramar(torneo, partidoId, torneo.primeraJornada.plusDays(3).atTime(10, 0),
                torneo.canchaA, "Lluvia")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("cerrado")));
    }

    private ResultActions reprogramar(EscenarioTorneo torneo, long partidoId, LocalDateTime fecha,
            long canchaId, String motivo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fechaInicio", fecha.toString());
        body.put("canchaId", canchaId);
        body.put("motivo", motivo);
        body.put("usuarioId", torneo.usuarioId);
        return torneo.postJson(torneo.partido(partidoId) + "/reprogramaciones", body);
    }
}
