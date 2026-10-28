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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 12 - Goleadores y estadísticas: tareas S4-11 (las tablas del torneo) y
 * S4-12 (resumen por jugador). Todo sale de los partidos cerrados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class EstadisticasApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /**
     * Un 2-2 con un autogol: el autogol suma al rival pero no al goleador, la
     * asistencia cuenta, y las tarjetas van al fair play.
     */
    @Test
    void lasTablasDelTorneoSalenDeLosPartidosCerrados() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        JsonNode partido = torneo.generarLiga().get(0);
        long id = partido.get("id").asLong();
        long local = partido.get("clubLocalId").asLong();
        long visitante = partido.get("clubVisitanteId").asLong();
        List<Long> l = torneo.equipoDe(local).jugadores();
        List<Long> v = torneo.equipoDe(visitante).jugadores();
        String estadisticas = EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/categorias/"
                + torneo.categoriaId + "/estadisticas";

        torneo.planillaCompleta(id, local, visitante, 2).andExpect(status().isOk());
        torneo.confirmarPlanilla(id);
        torneo.evento(id, "GOL", 10, l.get(0), l.get(1), "JUGADA", null).andExpect(status().isCreated());
        torneo.evento(id, "GOL", 20, l.get(0), null, "PENAL", null).andExpect(status().isCreated());
        torneo.evento(id, "GOL", 30, v.get(0), null, "JUGADA", null).andExpect(status().isCreated());
        torneo.evento(id, "GOL", 40, l.get(1), null, "AUTOGOL", null).andExpect(status().isCreated());
        torneo.evento(id, "AMARILLA", 50, v.get(1), null, null, "Mano").andExpect(status().isCreated());

        // Sin cerrar, todavía no cuenta.
        mvc.perform(get(estadisticas)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosCerrados").value(0))
                .andExpect(jsonPath("$.goleadores.length()").value(0));

        torneo.cerrar(id, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.golesLocal").value(2))
                .andExpect(jsonPath("$.golesVisitante").value(2));

        mvc.perform(get(estadisticas)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosCerrados").value(1))
                .andExpect(jsonPath("$.goleadores.length()").value(2))
                .andExpect(jsonPath("$.goleadores[0].jugadorId").value(l.get(0)))
                .andExpect(jsonPath("$.goleadores[0].cantidad").value(2))
                .andExpect(jsonPath("$.goleadores[0].clubId").value(local))
                .andExpect(jsonPath("$.goleadores[0].partidosJugados").value(1))
                .andExpect(jsonPath("$.goleadores[1].jugadorId").value(v.get(0)))
                .andExpect(jsonPath("$.goleadores[1].posicion").value(2))
                .andExpect(jsonPath("$.asistencias.length()").value(1))
                .andExpect(jsonPath("$.asistencias[0].jugadorId").value(l.get(1)))
                .andExpect(jsonPath("$.vallasMenosVencidas.length()").value(2))
                .andExpect(jsonPath("$.vallasMenosVencidas[0].golesEnContra").value(2))
                .andExpect(jsonPath("$.vallasMenosVencidas[0].promedio").value(2.0))
                .andExpect(jsonPath("$.vallasMenosVencidas[0].vallasInvictas").value(0))
                .andExpect(jsonPath("$.fairPlay[0].clubId").value(local))
                .andExpect(jsonPath("$.fairPlay[0].puntos").value(0))
                .andExpect(jsonPath("$.fairPlay[1].clubId").value(visitante))
                .andExpect(jsonPath("$.fairPlay[1].amarillas").value(1))
                .andExpect(jsonPath("$.fairPlay[1].puntos").value(1));

        mvc.perform(get(estadisticas).param("limite", "1"))
                .andExpect(jsonPath("$.goleadores.length()").value(1));
        mvc.perform(get(estadisticas).param("limite", "0")).andExpect(status().isBadRequest());
    }

    /** S4-12: partidos, minutos, goles, asistencias, autogoles y tarjetas de cada jugador. */
    @Test
    void elResumenDelJugadorSumaSusPartidosCerrados() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        JsonNode partido = torneo.generarLiga().get(0);
        long id = partido.get("id").asLong();
        long local = partido.get("clubLocalId").asLong();
        long visitante = partido.get("clubVisitanteId").asLong();
        List<Long> l = torneo.equipoDe(local).jugadores();
        torneo.planillaCompleta(id, local, visitante, 2).andExpect(status().isOk());
        torneo.confirmarPlanilla(id);
        torneo.evento(id, "GOL", 10, l.get(0), l.get(1), "TIRO_LIBRE", null).andExpect(status().isCreated());
        torneo.evento(id, "AMARILLA", 25, l.get(0), null, null, "Demora").andExpect(status().isCreated());
        torneo.evento(id, "CAMBIO", 40, l.get(1), l.get(2), null, null).andExpect(status().isCreated());
        torneo.evento(id, "GOL", 60, l.get(2), null, "AUTOGOL", null).andExpect(status().isCreated());
        torneo.cerrar(id, Map.of()).andExpect(status().isOk());

        mvc.perform(get(resumen(torneo, l.get(0)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.partidosTitular").value(1))
                .andExpect(jsonPath("$.minutosJugados").value(70))
                .andExpect(jsonPath("$.goles").value(1))
                .andExpect(jsonPath("$.amarillas").value(1))
                .andExpect(jsonPath("$.rojas").value(0));
        mvc.perform(get(resumen(torneo, l.get(1))))
                .andExpect(jsonPath("$.minutosJugados").value(40))
                .andExpect(jsonPath("$.asistencias").value(1))
                .andExpect(jsonPath("$.goles").value(0));
        mvc.perform(get(resumen(torneo, l.get(2))))
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.partidosTitular").value(0))
                .andExpect(jsonPath("$.minutosJugados").value(30))
                .andExpect(jsonPath("$.goles").value(0))
                .andExpect(jsonPath("$.autogoles").value(1));

        // El jugador tiene que estar en el torneo, y el torneo existir.
        mvc.perform(get(resumen(torneo, Long.MAX_VALUE))).andExpect(status().isNotFound());
        mvc.perform(get(EscenarioTorneo.TORNEOS + "/" + Long.MAX_VALUE + "/jugadores/" + l.get(0)
                + "/estadisticas")).andExpect(status().isNotFound());
    }

    private String resumen(EscenarioTorneo torneo, long jugadorId) {
        return EscenarioTorneo.TORNEOS + "/" + torneo.torneoId + "/jugadores/" + jugadorId + "/estadisticas";
    }
}
