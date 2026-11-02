package edu.upb.semillero.controller.torneos;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 13 - Suspensión automática por tarjetas: tareas S4-01 (acumulación), S4-02
 * (suspensión por roja), S4-03 (descuento y bloqueo en la planilla), S4-04
 * (sancionados por fecha) y S4-05 (sanción manual y apelación).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class RegimenDisciplinarioApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /**
     * S4-02, S4-03 y S4-04: la roja suspende la fecha siguiente del equipo, el
     * jugador aparece bloqueado en esa planilla y en la lista de sancionados de
     * esa fecha, y al cerrarse el partido la sanción queda cumplida. Reabrir el
     * partido devuelve la fecha, y volver a cerrarlo no la cuenta dos veces.
     */
    @Test
    void laRojaSuspendeLaFechaSiguienteYSeDescuentaAlJugarla() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 3, 3);
        JsonNode fixture = torneo.generarLiga();
        JsonNode primero = fixture.get(0);
        long partidoId = primero.get("id").asLong();
        long local = primero.get("clubLocalId").asLong();
        long visitante = primero.get("clubVisitanteId").asLong();
        long expulsado = torneo.equipoDe(visitante).jugadores().get(0);
        torneo.planillaCompleta(partidoId, local, visitante, 2).andExpect(status().isOk());
        torneo.confirmarPlanilla(partidoId);
        torneo.evento(partidoId, "ROJA", 30, expulsado, null, null, "Agresión").andExpect(status().isCreated());
        torneo.cerrar(partidoId, Map.of()).andExpect(status().isOk());

        JsonNode siguiente = torneo.partidoDe(fixture, visitante, partidoId);
        long siguienteId = siguiente.get("id").asLong();
        int jornada = siguiente.get("jornada").asInt();
        JsonNode sanciones = leer(get(sanciones(torneo)));
        assertThat(sanciones).hasSize(1);
        JsonNode sancion = sanciones.get(0);
        long sancionId = sancion.get("id").asLong();
        assertThat(sancion.get("origen").asText()).isEqualTo("ROJA");
        assertThat(sancion.get("estado").asText()).isEqualTo("VIGENTE");
        assertThat(sancion.get("jugadorId").asLong()).isEqualTo(expulsado);
        assertThat(sancion.get("partidoId").asLong()).isEqualTo(partidoId);
        assertThat(sancion.get("fechasPendientes").asInt()).isEqualTo(1);
        assertThat(sancion.get("motivo").asText()).contains("Agresión");
        assertThat(sancion.get("jornadasSuspendido").get(0).asInt()).isEqualTo(jornada);

        // S4-03: bloqueado en la planilla de la fecha que cumple.
        String lado = siguiente.get("clubLocalId").asLong() == visitante ? "local" : "visitante";
        mvc.perform(get(torneo.partido(siguienteId) + "/planilla"))
                .andExpect(jsonPath("$." + lado + ".jugadores[0].jugadorId").value(expulsado))
                .andExpect(jsonPath("$." + lado + ".jugadores[0].bloqueado").value(true));
        // S4-04: en la lista de sancionados de esa fecha, y no de otra.
        mvc.perform(get(sancionados(torneo, jornada))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jugadorId").value(expulsado));
        mvc.perform(get(sancionados(torneo, 1))).andExpect(jsonPath("$.length()").value(0));

        long rival = siguiente.get("clubLocalId").asLong() == visitante
                ? siguiente.get("clubVisitanteId").asLong() : siguiente.get("clubLocalId").asLong();
        torneo.cerrar(siguienteId, Map.of("ganadorWalkoverId", rival)).andExpect(status().isOk());
        mvc.perform(get(sancion(sancionId)))
                .andExpect(jsonPath("$.estado").value("CUMPLIDA"))
                .andExpect(jsonPath("$.fechasCumplidas").value(1))
                .andExpect(jsonPath("$.fechasPendientes").value(0));

        torneo.postJson(torneo.partido(siguienteId) + "/reabrir",
                        Map.of("motivo", "W.O. mal cargado", "usuarioId", torneo.usuarioId))
                .andExpect(status().isOk());
        mvc.perform(get(sancion(sancionId)))
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.fechasCumplidas").value(0));
        torneo.cerrar(siguienteId, Map.of("ganadorWalkoverId", rival)).andExpect(status().isOk());
        mvc.perform(get(sancion(sancionId)))
                .andExpect(jsonPath("$.estado").value("CUMPLIDA"))
                .andExpect(jsonPath("$.fechasCumplidas").value(1));
        mvc.perform(get(sancion(sancionId) + "/historial")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accion", hasItems("SANCION_AUTOMATICA",
                        "DESCONTAR_FECHA", "REVERTIR_DESCUENTO")));

        mvc.perform(get(EscenarioTorneo.TORNEOS + "/jugadores/" + expulsado + "/sanciones")
                        .param("vigentes", "true"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(EscenarioTorneo.TORNEOS + "/jugadores/" + expulsado + "/sanciones"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * S4-01: tres amarillas en el torneo (umbral por defecto del reglamento),
     * repartidas en dos partidos, suspenden. Si se corrige el partido y ya no
     * llega al umbral, la suspensión se anula.
     */
    @Test
    void lasAmarillasSeAcumulanEnElTorneoHastaElUmbral() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 3, 3);
        JsonNode fixture = torneo.generarLiga();
        JsonNode primero = fixture.get(0);
        long equipo = primero.get("clubLocalId").asLong();
        long amonestado = torneo.equipoDe(equipo).jugadores().get(0);
        long primeroId = primero.get("id").asLong();
        JsonNode segundo = torneo.partidoDe(fixture, equipo, primeroId);
        long segundoId = segundo.get("id").asLong();

        jugar(torneo, primero);
        torneo.evento(primeroId, "AMARILLA", 10, amonestado, null, null, "Mano").andExpect(status().isCreated());
        torneo.evento(primeroId, "AMARILLA", 20, amonestado, null, null, "Demora").andExpect(status().isCreated());
        torneo.cerrar(primeroId, Map.of()).andExpect(status().isOk());
        assertThat(leer(get(sanciones(torneo)))).isEmpty();

        jugar(torneo, segundo);
        long amarilla = idDe(torneo.evento(segundoId, "AMARILLA", 15, amonestado, null, null, "Protesta")
                .andExpect(status().isCreated()));
        torneo.cerrar(segundoId, Map.of()).andExpect(status().isOk());
        JsonNode sanciones = leer(get(sanciones(torneo)));
        assertThat(sanciones).hasSize(1);
        assertThat(sanciones.get(0).get("origen").asText()).isEqualTo("ACUMULACION_AMARILLAS");
        assertThat(sanciones.get(0).get("jugadorId").asLong()).isEqualTo(amonestado);
        assertThat(sanciones.get(0).get("motivo").asText()).contains("3 amarillas");
        long sancionId = sanciones.get(0).get("id").asLong();

        // Se corrige el segundo partido: la tercera amarilla no existió.
        torneo.postJson(torneo.partido(segundoId) + "/reabrir",
                        Map.of("motivo", "Amarilla mal cargada", "usuarioId", torneo.usuarioId))
                .andExpect(status().isOk());
        mvc.perform(delete(torneo.partido(segundoId) + "/eventos/" + amarilla))
                .andExpect(status().isNoContent());
        torneo.cerrar(segundoId, Map.of()).andExpect(status().isOk());
        mvc.perform(get(sancion(sancionId))).andExpect(jsonPath("$.estado").value("ANULADA"));
        mvc.perform(get(sanciones(torneo)).param("estado", "VIGENTE"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** S4-05: sanción manual con motivo, apelación y resolución reducida. */
    @Test
    void laSancionManualSeApelaYSeReduce() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        long jugador = torneo.equipos.get(0).jugadores().get(0);

        manual(torneo, jugador, 3, " ").andExpect(status().isBadRequest());
        manual(torneo, Long.MAX_VALUE, 3, "Insultos").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no está en ninguna lista")));
        long sancionId = idDe(manual(torneo, jugador, 3, "Insultos al árbitro")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.fechasSuspension").value(3))
                .andExpect(jsonPath("$.usuarioId").value(torneo.usuarioId)));

        long apelacionId = idDe(apelar(torneo, sancionId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")));
        mvc.perform(get(sancion(sancionId))).andExpect(jsonPath("$.apelacionPendiente").value(true));
        apelar(torneo, sancionId).andExpect(status().isConflict());

        resolver(torneo, apelacionId, "REDUCIDA", null).andExpect(status().isBadRequest());
        resolver(torneo, apelacionId, "REDUCIDA", 3).andExpect(status().isBadRequest());
        resolver(torneo, apelacionId, "PENDIENTE", null).andExpect(status().isBadRequest());
        resolver(torneo, apelacionId, "REDUCIDA", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REDUCIDA"))
                .andExpect(jsonPath("$.fechasResueltas").value(1))
                .andExpect(jsonPath("$.resueltoPorId").value(torneo.usuarioId));
        resolver(torneo, apelacionId, "RECHAZADA", null).andExpect(status().isConflict());

        mvc.perform(get(sancion(sancionId)))
                .andExpect(jsonPath("$.fechasSuspension").value(1))
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.apelacionPendiente").value(false));
        mvc.perform(get(sancion(sancionId) + "/apelaciones")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(sancion(sancionId) + "/historial"))
                .andExpect(jsonPath("$[*].accion", hasItems("SANCION_MANUAL", "APELAR",
                        "RESOLVER_APELACION")));
    }

    /** S4-05: aceptada, la sanción se anula y ya no se puede apelar. */
    @Test
    void laApelacionAceptadaAnulaLaSancion() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        long jugador = torneo.equipos.get(1).jugadores().get(1);
        long sancionId = idDe(manual(torneo, jugador, 2, "Conducta antideportiva")
                .andExpect(status().isCreated()));
        long apelacionId = idDe(apelar(torneo, sancionId).andExpect(status().isCreated()));

        resolver(torneo, apelacionId, "RECHAZADA", 1).andExpect(status().isBadRequest());
        resolver(torneo, apelacionId, "ACEPTADA", null).andExpect(status().isOk());

        mvc.perform(get(sancion(sancionId))).andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.fechasPendientes").value(0));
        apelar(torneo, sancionId).andExpect(status().isBadRequest());
        mvc.perform(get(sanciones(torneo)).param("estado", "ANULADA"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(sancion(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }

    /** Guarda y confirma la planilla del partido con dos titulares por equipo. */
    private void jugar(EscenarioTorneo torneo, JsonNode partido) throws Exception {
        long id = partido.get("id").asLong();
        torneo.planillaCompleta(id, partido.get("clubLocalId").asLong(),
                partido.get("clubVisitanteId").asLong(), 2).andExpect(status().isOk());
        torneo.confirmarPlanilla(id);
    }

    private ResultActions manual(EscenarioTorneo torneo, long jugadorId, int fechas, String motivo)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jugadorId", jugadorId);
        body.put("fechasSuspension", fechas);
        body.put("motivo", motivo);
        body.put("usuarioId", torneo.usuarioId);
        return torneo.postJson(sanciones(torneo), body);
    }

    private ResultActions apelar(EscenarioTorneo torneo, long sancionId) throws Exception {
        return torneo.postJson(sancion(sancionId) + "/apelaciones",
                Map.of("motivo", "El jugador no fue el que insultó", "usuarioId", torneo.usuarioId));
    }

    private ResultActions resolver(EscenarioTorneo torneo, long apelacionId, String estado,
            Integer fechas) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("estado", estado);
        body.put("resolucion", "Revisado el informe del árbitro");
        if (fechas != null) {
            body.put("fechasResueltas", fechas);
        }
        body.put("usuarioId", torneo.usuarioId);
        return torneo.putJson(EscenarioTorneo.TORNEOS + "/apelaciones/" + apelacionId + "/resolucion", body);
    }

    private String sanciones(EscenarioTorneo torneo) {
        return EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/sanciones";
    }

    private String sancionados(EscenarioTorneo torneo, int jornada) {
        return EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/categorias/" + torneo.categoriaId
                + "/sancionados?jornada=" + jornada;
    }

    private String sancion(long sancionId) {
        return EscenarioTorneo.TORNEOS + "/sanciones/" + sancionId;
    }

    private JsonNode leer(org.springframework.test.web.servlet.RequestBuilder pedido) throws Exception {
        return json.readTree(mvc.perform(pedido).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    private long idDe(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
