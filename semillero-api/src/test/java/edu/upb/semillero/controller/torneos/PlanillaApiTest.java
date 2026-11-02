package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.SancionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

import static edu.upb.semillero.controller.torneos.EscenarioTorneo.jugador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 9 - Planilla del partido: tareas S3-01 (precargada), S3-02 (titulares,
 * suplentes, capitán y bloqueados), S3-03 (cuerpo técnico, borrador y
 * confirmación) y S3-04 (PDF).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class PlanillaApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ListaBuenaFeRepository listas;
    @Autowired SancionRepository sanciones;
    @Autowired JugadorRepository jugadores;

    /** Tarea S3-03: se guarda incompleta, se retoma, y se confirma solo completa. */
    @Test
    void seGuardaIncompletaYSeConfirmaCuandoEstaCompleta() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = primerPartido(torneo);
        List<Long> local = torneo.equipoDe(partido.local()).jugadores();

        guardar(torneo, partido.id(), List.of(jugador(local.get(0), true, true),
                jugador(local.get(1), false, false)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planillaId").exists())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.local.titulares").value(1))
                .andExpect(jsonPath("$.local.suplentes").value(1))
                .andExpect(jsonPath("$.local.jugadores[0].enPlanilla").value(true))
                .andExpect(jsonPath("$.local.jugadores[0].capitan").value(true))
                .andExpect(jsonPath("$.local.jugadores[0].dorsal").value(1))
                .andExpect(jsonPath("$.local.jugadores[2].enPlanilla").value(false))
                .andExpect(jsonPath("$.visitante.titulares").value(0));

        // Al visitante le faltan titulares y capitán.
        mvc.perform(post(torneo.partido(partido.id()) + "/planilla/confirmar")
                        .param("usuarioId", String.valueOf(torneo.usuarioId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no tiene titulares")));

        torneo.planillaCompleta(partido.id(), partido.local(), partido.visitante(), 2)
                .andExpect(status().isOk());
        torneo.confirmarPlanilla(partido.id());
        mvc.perform(get(torneo.partido(partido.id()) + "/planilla"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.fechaConfirmacion").exists())
                .andExpect(jsonPath("$.visitante.cuerpoTecnico[0].nombreCompleto").value("Luis Rojas"));
        mvc.perform(post(torneo.partido(partido.id()) + "/planilla/confirmar")
                        .param("usuarioId", String.valueOf(torneo.usuarioId)))
                .andExpect(status().isConflict());

        // Confirmada se puede corregir, pero sin dejar a un equipo sin capitán.
        List<Map<String, Object>> sinCapitan = new ArrayList<>();
        for (long jugadorId : local) {
            sinCapitan.add(jugador(jugadorId, true, false));
        }
        guardar(torneo, partido.id(), sinCapitan, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no tiene capitán")));
    }

    /** Tarea S3-02: solo jugadores de las dos listas, sin repetir, y las cantidades de la categoría. */
    @Test
    void validaLosJugadoresDeLaPlanilla() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 3, 8);
        Partido partido = primerPartido(torneo);
        List<Long> local = torneo.equipoDe(partido.local()).jugadores();
        long ajeno = torneo.equipos.stream()
                .filter(e -> e.clubId() != partido.local() && e.clubId() != partido.visitante())
                .findFirst().orElseThrow().jugadores().get(0);

        guardar(torneo, partido.id(), List.of(jugador(ajeno, true, false)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no está en la lista de buena fe")));
        guardar(torneo, partido.id(), List.of(jugador(local.get(0), true, false),
                jugador(local.get(0), false, false)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("dos veces")));
        guardar(torneo, partido.id(), List.of(jugador(local.get(0), true, true),
                jugador(local.get(1), true, true)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("capitanes")));

        // La categoría juega con 7: el octavo titular no entra.
        List<Map<String, Object>> ocho = new ArrayList<>();
        for (long jugadorId : local) {
            ocho.add(jugador(jugadorId, true, false));
        }
        guardar(torneo, partido.id(), ocho, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("juegan 7")));

        Map<String, Object> cuerpoAjeno = Map.of("clubId", Long.MAX_VALUE,
                "nombreCompleto", "Pedro Ruiz", "cargo", "PF");
        guardar(torneo, partido.id(), List.of(jugador(local.get(0), true, true)), List.of(cuerpoAjeno))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no juega este partido")));

        mvc.perform(get(torneo.partido(Long.MAX_VALUE) + "/planilla")).andExpect(status().isNotFound());
    }

    /** Tarea S3-02: el suspendido y el que no está ACTIVO aparecen bloqueados y no entran. */
    @Test
    void losSuspendidosYLosNoHabilitadosAparecenBloqueados() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = primerPartido(torneo);
        List<Long> local = torneo.equipoDe(partido.local()).jugadores();
        suspender(torneo, local.get(0));
        Jugador lesionado = jugadores.findById(local.get(1)).orElseThrow();
        lesionado.setEstado(EstadoJugador.LESIONADO);
        jugadores.saveAndFlush(lesionado);

        mvc.perform(get(torneo.partido(partido.id()) + "/planilla")).andExpect(status().isOk())
                .andExpect(jsonPath("$.local.jugadores[0].bloqueado").value(true))
                .andExpect(jsonPath("$.local.jugadores[0].motivoBloqueo").value(
                        containsString("suspendido")))
                .andExpect(jsonPath("$.local.jugadores[1].bloqueado").value(true))
                .andExpect(jsonPath("$.local.jugadores[1].motivoBloqueo").value(
                        containsString("LESIONADO")))
                .andExpect(jsonPath("$.local.jugadores[2].bloqueado").value(false));

        guardar(torneo, partido.id(), List.of(jugador(local.get(0), true, true)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("bloqueado")));
        guardar(torneo, partido.id(), List.of(jugador(local.get(2), true, true)), null)
                .andExpect(status().isOk());
    }

    /** Tarea S3-04: la planilla en PDF, también antes de guardarla. */
    @Test
    void exportaLaPlanillaEnPdf() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        Partido partido = primerPartido(torneo);

        byte[] pdf = mvc.perform(get(torneo.partido(partido.id()) + "/planilla/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(800);
    }

    /** Un partido cerrado ya no cambia su planilla. */
    @Test
    void noSeModificaLaPlanillaDeUnPartidoCerrado() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        Partido partido = primerPartido(torneo);
        torneo.cerrar(partido.id(), Map.of("ganadorWalkoverId", partido.local()))
                .andExpect(status().isOk());

        torneo.planillaCompleta(partido.id(), partido.local(), partido.visitante(), 1)
                .andExpect(status().isConflict());
    }

    private void suspender(EscenarioTorneo torneo, long jugadorId) {
        ListaBuenaFe enLista = listas.findByTorneoIdAndJugadorId(torneo.torneoId, jugadorId)
                .orElseThrow();
        Sancion sancion = new Sancion();
        sancion.setListaBuenaFe(enLista);
        sancion.setOrigen(OrigenSancion.ROJA);
        sancion.setFechasSuspension((short) 2);
        sancion.setFechasCumplidas((short) 0);
        sancion.setEstado(EstadoSancion.VIGENTE);
        sancion.setFechaRegistro(LocalDateTime.now());
        sanciones.saveAndFlush(sancion);
    }

    private ResultActions guardar(EscenarioTorneo torneo, long partidoId,
            List<Map<String, Object>> jugadoresEnPlanilla, List<Map<String, Object>> cuerpoTecnico)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jugadores", jugadoresEnPlanilla);
        if (cuerpoTecnico != null) {
            body.put("cuerpoTecnico", cuerpoTecnico);
        }
        body.put("observacionesArbitro", "Sin novedad");
        body.put("usuarioId", torneo.usuarioId);
        return torneo.putJson(torneo.partido(partidoId) + "/planilla", body);
    }

    private Partido primerPartido(EscenarioTorneo torneo) throws Exception {
        JsonNode partido = torneo.generarLiga().get(0);
        return new Partido(partido.get("id").asLong(), partido.get("clubLocalId").asLong(),
                partido.get("clubVisitanteId").asLong());
    }

    private record Partido(long id, long local, long visitante) {}
}
