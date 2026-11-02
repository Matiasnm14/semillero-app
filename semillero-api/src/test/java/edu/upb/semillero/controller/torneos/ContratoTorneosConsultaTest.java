package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import edu.upb.semillero.service.torneos.EstadisticasJugadorDto;
import edu.upb.semillero.service.torneos.FilaTablaDto;
import edu.upb.semillero.service.torneos.PartidoTorneoDto;
import edu.upb.semillero.service.torneos.SancionDto;
import edu.upb.semillero.service.torneos.TorneoDto;
import edu.upb.semillero.service.torneos.TorneosConsultaService;
import java.time.LocalDate;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tarea S4-18 - Integración entre módulos: lo que ficha técnica y scouting
 * reciben por {@code TorneosConsultaService} (punto 12 del acuerdo de la S1-04)
 * coincide con lo que la API REST muestra del mismo torneo. El torneo se arma
 * por la API y se consulta por la interfaz Java, que es como lo van a usar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class ContratoTorneosConsultaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TorneosConsultaService consulta;

    @Test
    void laInterfazJavaDaLoMismoQueLaApi() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long partidoId = partido.get("id").asLong();
        long local = partido.get("clubLocalId").asLong();
        long visitante = partido.get("clubVisitanteId").asLong();
        long goleador = torneo.equipoDe(local).jugadores().get(0);
        long expulsado = torneo.equipoDe(visitante).jugadores().get(0);
        torneo.planillaCompleta(partidoId, local, visitante, 2).andExpect(status().isOk());
        torneo.confirmarPlanilla(partidoId);
        torneo.evento(partidoId, "GOL", 12, goleador, null, "JUGADA", null).andExpect(status().isCreated());
        torneo.evento(partidoId, "ROJA", 50, expulsado, null, null, "Agresión").andExpect(status().isCreated());
        torneo.cerrar(partidoId, Map.of()).andExpect(status().isOk());

        List<TorneoDto> temporada = consulta.torneosDeTemporada((short) LocalDate.now().getYear());
        assertThat(temporada).extracting(TorneoDto::torneoId).contains(torneo.torneoId);

        List<PartidoTorneoDto> fixture = consulta.fixtureDeCategoria(torneo.categoriaId);
        assertThat(fixture).hasSize(1);
        assertThat(fixture.get(0).partidoId()).isEqualTo(partidoId);
        assertThat(fixture.get(0).golesLocal()).isEqualTo((short) 1);

        List<FilaTablaDto> tabla = consulta.tablaDeCategoria(torneo.categoriaId);
        assertThat(tabla).hasSize(2);
        assertThat(tabla.get(0).clubId()).isEqualTo(local);
        assertThat(tabla.get(0).puntos()).isEqualTo(3);

        EstadisticasJugadorDto estadisticas = consulta.estadisticasDeJugador(goleador, torneo.torneoId)
                .orElseThrow();
        assertThat(estadisticas.goles()).isEqualTo(1);
        assertThat(estadisticas.minutosJugados()).isEqualTo(70);
        assertThat(estadisticas.partidosJugados()).isEqualTo(1);

        List<SancionDto> sanciones = consulta.sancionesVigentesDeJugador(expulsado);
        assertThat(sanciones).hasSize(1);
        assertThat(sanciones.get(0).origen()).isEqualTo(OrigenSancion.ROJA);
        assertThat(sanciones.get(0).estado()).isEqualTo(EstadoSancion.VIGENTE);
        assertThat(sanciones.get(0).torneoId()).isEqualTo(torneo.torneoId);
    }

    /** Lo que no existe vuelve vacío: la interfaz no le tira excepciones a otro módulo. */
    @Test
    void loQueNoExisteVuelveVacio() {
        assertThat(consulta.fixtureDeCategoria(Long.MAX_VALUE)).isEmpty();
        assertThat(consulta.tablaDeCategoria(Long.MAX_VALUE)).isEmpty();
        assertThat(consulta.tablaDeCategoria(null)).isEmpty();
        assertThat(consulta.estadisticasDeJugador(Long.MAX_VALUE, Long.MAX_VALUE)).isEmpty();
        assertThat(consulta.sancionesVigentesDeJugador(Long.MAX_VALUE)).isEmpty();
        assertThat(consulta.torneosDeTemporada((short) 1900)).isEmpty();
    }

    /** Un torneo en BORRADOR no se ve todavía (HU 1). */
    @Test
    void unTorneoEnBorradorNoSeVe() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 0, 0);
        Map<String, Object> borrador = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.now();
        borrador.put("nombre", "Copa en borrador");
        borrador.put("temporada", hoy.getYear());
        borrador.put("tipo", "AMISTOSO");
        borrador.put("ambito", "INTERNO");
        borrador.put("formato", "LIGA");
        borrador.put("fechaInicio", hoy.plusDays(20).toString());
        borrador.put("fechaFin", hoy.plusDays(60).toString());
        borrador.put("fechaLimiteInscripcion", hoy.plusDays(10).toString());
        borrador.put("usuarioId", torneo.usuarioId);
        long borradorId = torneo.create(EscenarioTorneo.TORNEOS, borrador);

        assertThat(consulta.torneosDeTemporada((short) hoy.getYear()))
                .extracting(TorneoDto::torneoId)
                .contains(torneo.torneoId)
                .doesNotContain(borradorId);
    }
}
