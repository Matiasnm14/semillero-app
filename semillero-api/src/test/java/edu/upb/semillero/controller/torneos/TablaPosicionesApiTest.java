package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.repos.torneos.GrupoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 11 - Tabla de posiciones por la API: tareas S3-12 (cálculo desde los
 * partidos cerrados) y S3-14 (por categoría y por grupo). Los desempates
 * (S3-13) se prueban caso por caso en {@code TablaPosicionesServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class TablaPosicionesApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GrupoRepository grupos;
    @Autowired TorneoCategoriaRepository categorias;

    /** Tarea S3-12: todos los equipos aprobados aparecen, aunque no hayan jugado. */
    @Test
    void seCalculaDesdeLosPartidosCerrados() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 3, 2);
        JsonNode fixture = torneo.generarLiga();

        mvc.perform(get(torneo.tabla())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grupoId").doesNotExist())
                .andExpect(jsonPath("$[0].partidosCerrados").value(0))
                .andExpect(jsonPath("$[0].filas.length()").value(3))
                .andExpect(jsonPath("$[0].filas[0].puntos").value(0))
                .andExpect(jsonPath("$[0].desempates[0]").value("DIF_GOLES"));

        // Un W.O. cuenta como partido ganado por los goles del reglamento.
        long ganador = fixture.get(0).get("clubVisitanteId").asLong();
        torneo.cerrar(fixture.get(0).get("id").asLong(), Map.of("ganadorWalkoverId", ganador))
                .andExpect(status().isOk());

        mvc.perform(get(torneo.tabla())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partidosCerrados").value(1))
                .andExpect(jsonPath("$[0].filas[0].posicion").value(1))
                .andExpect(jsonPath("$[0].filas[0].clubId").value(ganador))
                .andExpect(jsonPath("$[0].filas[0].puntos").value(3))
                .andExpect(jsonPath("$[0].filas[0].partidosJugados").value(1))
                .andExpect(jsonPath("$[0].filas[0].ganados").value(1))
                .andExpect(jsonPath("$[0].filas[0].golesFavor").value(3))
                .andExpect(jsonPath("$[0].filas[0].diferenciaGoles").value(3))
                .andExpect(jsonPath("$[0].filas[2].perdidos").value(1))
                .andExpect(jsonPath("$[0].filas[2].golesContra").value(3));
    }

    /** Tarea S3-14: una tabla por grupo, y se puede pedir la de un grupo solo. */
    @Test
    void enElFormatoDeGruposHayUnaTablaPorGrupo() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.con(mvc, json, "GRUPOS_FASE_FINAL", false, 0, 0,
                List.of("DIF_GOLES", "GOLES_FAVOR"));
        Grupo a = grupo(torneo, "A");
        Grupo b = grupo(torneo, "B");
        torneo.equipoInscrito(0, a.getId());
        torneo.equipoInscrito(0, a.getId());
        torneo.equipoInscrito(0, b.getId());
        torneo.equipoInscrito(0, b.getId());
        JsonNode fixture = torneo.generarLiga();
        JsonNode delGrupoA = null;
        for (JsonNode partido : fixture) {
            if (partido.get("grupoId").asLong() == a.getId()) {
                delGrupoA = partido;
            }
        }
        torneo.cerrar(delGrupoA.get("id").asLong(),
                        Map.of("ganadorWalkoverId", delGrupoA.get("clubLocalId").asLong()))
                .andExpect(status().isOk());

        mvc.perform(get(torneo.tabla())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].grupoNombre").value("A"))
                .andExpect(jsonPath("$[0].partidosCerrados").value(1))
                .andExpect(jsonPath("$[0].filas.length()").value(2))
                .andExpect(jsonPath("$[0].filas[0].puntos").value(3))
                .andExpect(jsonPath("$[1].grupoNombre").value("B"))
                .andExpect(jsonPath("$[1].partidosCerrados").value(0));
        mvc.perform(get(torneo.tabla()).param("grupoId", String.valueOf(b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grupoId").value(b.getId()));
        mvc.perform(get(torneo.tabla()).param("grupoId", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    /** HU 11: en un campeonato externo y en eliminación directa no hay tabla. */
    @Test
    void noHayTablaEnUnCampeonatoExternoNiEnEliminacionDirecta() throws Exception {
        EscenarioTorneo externo = EscenarioTorneo.con(mvc, json, "LIGA", true, 0, 0,
                List.of("DIF_GOLES"));
        mvc.perform(get(externo.tabla())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("campeonato externo")));

        EscenarioTorneo copa = EscenarioTorneo.con(mvc, json, "ELIMINACION_DIRECTA", false, 0, 0,
                List.of("DIF_GOLES"));
        mvc.perform(get(copa.tabla())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("llaves")));

        EscenarioTorneo liga = EscenarioTorneo.liga(mvc, json, 0, 0);
        mvc.perform(get(liga.tabla()).param("grupoId", "1")).andExpect(status().isBadRequest());
        mvc.perform(get(EscenarioTorneo.TORNEOS + "/" + liga.torneoId + "/categorias/"
                        + Long.MAX_VALUE + "/tabla"))
                .andExpect(status().isNotFound());
    }

    private Grupo grupo(EscenarioTorneo torneo, String nombre) {
        Grupo grupo = new Grupo();
        grupo.setTorneoCategoria(categorias.findById(torneo.categoriaId).orElseThrow());
        grupo.setNombre(nombre);
        return grupos.saveAndFlush(grupo);
    }
}
