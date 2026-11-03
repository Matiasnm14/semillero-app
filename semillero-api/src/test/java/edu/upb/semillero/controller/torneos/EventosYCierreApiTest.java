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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 10 - Eventos, cierre y reapertura: tareas S3-06 (goles), S3-07 (tarjetas
 * y cambios), S3-09 (cierre en una transacción) y S3-10 (reapertura). Los
 * minutos (S3-08) están en {@code FlujoCompletoPartidoApiTest} y en
 * {@code MinutosJugadosTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class EventosYCierreApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** Sin planilla confirmada no hay eventos, y solo de jugadores de la planilla. */
    @Test
    void losEventosPidenLaPlanillaConfirmada() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = partidoConPlanilla(torneo, 2, false);

        torneo.evento(partido.id(), "GOL", 5, partido.l(0), null, "JUGADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no está confirmada")));

        torneo.confirmarPlanilla(partido.id());
        torneo.evento(partido.id(), "GOL", 5, Long.MAX_VALUE, null, "JUGADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no está en la planilla")));
        torneo.evento(partido.id(), "GOL", 131, partido.l(0), null, "JUGADA", null)
                .andExpect(status().isBadRequest());
        torneo.evento(partido.id(), "GOL", 5, partido.l(0), null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tipo")));

        // El primer evento pone el partido en juego.
        torneo.evento(partido.id(), "GOL", 5, partido.l(0), null, "PENAL", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoGol").value("PENAL"));
        mvc.perform(get(torneo.partido(partido.id()) + "/resultado"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));
    }

    /** Tarea S3-06: el autor y la asistencia en cancha; la asistencia, de un compañero. */
    @Test
    void validaLosGoles() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = partidoConPlanilla(torneo, 2, true);

        // El tercero es suplente y no entró.
        torneo.evento(partido.id(), "GOL", 10, partido.l(2), null, "JUGADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no está en cancha")));
        torneo.evento(partido.id(), "GOL", 10, partido.l(0), partido.v(0), "JUGADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("compañero")));
        torneo.evento(partido.id(), "GOL", 10, partido.l(0), partido.l(1), "AUTOGOL", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no lleva asistencia")));
        torneo.evento(partido.id(), "GOL", 10, partido.l(0), partido.l(1), "TIRO_LIBRE", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jugadorRelacionadoId").value(partido.l(1)));
    }

    /** Tarea S3-07: tarjetas con motivo, dos amarillas como mucho, y nada después de la roja. */
    @Test
    void validaLasTarjetas() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = partidoConPlanilla(torneo, 2, true);

        torneo.evento(partido.id(), "AMARILLA", 10, partido.v(0), null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("motivo")));
        torneo.evento(partido.id(), "AMARILLA", 10, partido.v(0), null, null, "Mano")
                .andExpect(status().isCreated());
        torneo.evento(partido.id(), "AMARILLA", 30, partido.v(0), null, null, "Protesta")
                .andExpect(status().isCreated());
        torneo.evento(partido.id(), "AMARILLA", 31, partido.v(0), null, null, "Protesta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("corresponde cargar la roja")));
        torneo.evento(partido.id(), "ROJA", 31, partido.v(0), null, null, "Doble amarilla")
                .andExpect(status().isCreated());
        torneo.evento(partido.id(), "GOL", 40, partido.v(0), null, "JUGADA", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("expulsado")));
        // Al suplente que está en el banco también se lo puede amonestar.
        torneo.evento(partido.id(), "AMARILLA", 45, partido.v(2), null, null, "Protesta desde el banco")
                .andExpect(status().isCreated());
    }

    /** Tarea S3-07: sale uno en cancha, entra un suplente del mismo equipo, y el máximo de cambios. */
    @Test
    void validaLosCambios() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 6);
        Partido partido = partidoConPlanilla(torneo, 3, true);

        torneo.evento(partido.id(), "CAMBIO", 20, partido.l(0), null, null, null)
                .andExpect(status().isBadRequest());
        torneo.evento(partido.id(), "CAMBIO", 20, partido.l(0), partido.l(1), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("ya jugó")));
        torneo.evento(partido.id(), "CAMBIO", 20, partido.l(0), partido.v(3), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("mismo equipo")));
        torneo.evento(partido.id(), "CAMBIO", 20, partido.l(3), partido.l(4), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no está en cancha")));

        torneo.evento(partido.id(), "CAMBIO", 20, partido.l(0), partido.l(3), null, null)
                .andExpect(status().isCreated());
        // El que salió no vuelve a entrar.
        torneo.evento(partido.id(), "CAMBIO", 25, partido.l(1), partido.l(0), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("ya jugó")));
        torneo.evento(partido.id(), "CAMBIO", 25, partido.l(1), partido.l(4), null, null)
                .andExpect(status().isCreated());
        // La categoría permite 2 cambios.
        torneo.evento(partido.id(), "CAMBIO", 30, partido.l(2), partido.l(5), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("2 cambios")));
        // El que entró ya puede hacer un gol.
        torneo.evento(partido.id(), "GOL", 40, partido.l(4), null, "JUGADA", null)
                .andExpect(status().isCreated());
    }

    /** Un evento mal cargado se borra; un cambio no, si el que entró ya tiene eventos. */
    @Test
    void borraEventosMalCargados() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = partidoConPlanilla(torneo, 2, true);
        long cambio = idDe(torneo.evento(partido.id(), "CAMBIO", 20, partido.l(0), partido.l(2),
                null, null).andExpect(status().isCreated()));
        long gol = idDe(torneo.evento(partido.id(), "GOL", 30, partido.l(2), null, "JUGADA", null)
                .andExpect(status().isCreated()));
        String eventos = torneo.partido(partido.id()) + "/eventos";

        mvc.perform(delete(eventos + "/" + cambio)).andExpect(status().isConflict());
        mvc.perform(delete(eventos + "/" + gol)).andExpect(status().isNoContent());
        mvc.perform(delete(eventos + "/" + cambio)).andExpect(status().isNoContent());
        mvc.perform(delete(eventos + "/" + cambio)).andExpect(status().isNotFound());
        mvc.perform(get(eventos)).andExpect(jsonPath("$.length()").value(0));
    }

    /** Tarea S3-09: los goles salen de los eventos; un partido cerrado no se toca. */
    @Test
    void cierraConLosGolesDeLosEventos() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = partidoConPlanilla(torneo, 2, true);
        torneo.evento(partido.id(), "GOL", 10, partido.v(0), null, "JUGADA", null)
                .andExpect(status().isCreated());

        torneo.cerrar(partido.id(), Map.of("golesLocal", 0, "golesVisitante", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("salen de los eventos")));
        torneo.cerrar(partido.id(), Map.of("penalesLocal", 3, "penalesVisitante", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("penales")));
        torneo.cerrar(partido.id(), Map.of("observacionesArbitro", "Partido limpio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.golesLocal").value(0))
                .andExpect(jsonPath("$.golesVisitante").value(1));
        mvc.perform(get(torneo.partido(partido.id()) + "/planilla"))
                .andExpect(jsonPath("$.observacionesArbitro").value("Partido limpio"));

        torneo.cerrar(partido.id(), Map.of()).andExpect(status().isConflict());
        torneo.evento(partido.id(), "GOL", 60, partido.v(0), null, "JUGADA", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("reabrirlo")));
    }

    /** Sin planilla confirmada no se cierra, salvo por W.O. */
    @Test
    void sinPlanillaSoloSeCierraPorWalkover() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode fixture = torneo.generarLiga().get(0);
        long partidoId = fixture.get("id").asLong();
        long visitante = fixture.get("clubVisitanteId").asLong();

        torneo.cerrar(partidoId, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no tiene planilla")));
        torneo.cerrar(partidoId, Map.of("ganadorWalkoverId", Long.MAX_VALUE))
                .andExpect(status().isBadRequest());
        torneo.cerrar(partidoId, Map.of("ganadorWalkoverId", visitante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.ganadorWalkoverId").value(visitante))
                .andExpect(jsonPath("$.golesLocal").value(0))
                .andExpect(jsonPath("$.golesVisitante").value(3));

        // Reabierto, el W.O. vuelve a estar por jugarse.
        torneo.postJson(torneo.partido(partidoId) + "/reabrir",
                        Map.of("motivo", "El rival se presentó", "usuarioId", torneo.usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.ganadorWalkoverId").doesNotExist());
    }

    /** Tarea S3-10: reabrir pide motivo, solo un partido cerrado, y queda en el historial. */
    @Test
    void reabreSoloUnPartidoCerradoYConMotivo() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 3);
        Partido partido = partidoConPlanilla(torneo, 2, true);
        String reabrir = torneo.partido(partido.id()) + "/reabrir";

        torneo.postJson(reabrir, Map.of("motivo", "Error", "usuarioId", torneo.usuarioId))
                .andExpect(status().isConflict());
        torneo.cerrar(partido.id(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.jugadores[0].minutosJugados").value(70));
        torneo.postJson(reabrir, Map.of("motivo", " ", "usuarioId", torneo.usuarioId))
                .andExpect(status().isBadRequest());
        torneo.postJson(reabrir, Map.of("motivo", "Faltó un gol", "usuarioId", torneo.usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.jugadores[0].minutosJugados").doesNotExist());
        mvc.perform(get(torneo.partido(partido.id()) + "/historial"))
                .andExpect(jsonPath("$[0].accion").value("REABRIR"))
                .andExpect(jsonPath("$[0].valorAnterior").value(containsString("FINALIZADO")))
                .andExpect(jsonPath("$[1].accion").value("CERRAR"));

        // Reabierto se corrige y se vuelve a cerrar.
        torneo.evento(partido.id(), "GOL", 15, partido.l(0), null, "JUGADA", null)
                .andExpect(status().isCreated());
        torneo.cerrar(partido.id(), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.golesLocal").value(1));
    }

    /** En un campeonato externo el rival no tiene planilla: el resultado se carga a mano. */
    @Test
    void enUnCampeonatoExternoElResultadoSeCargaAMano() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.con(mvc, json, "LIGA", true, 1, 2,
                List.of("DIF_GOLES"));
        EscenarioTorneo.Equipo nuestro = torneo.equipos.get(0);
        long rival = torneo.clubSinInscribir();
        Map<String, Object> carga = new LinkedHashMap<>();
        carga.put("clubLocalId", nuestro.clubId());
        carga.put("clubVisitanteId", rival);
        carga.put("fechaInicio", torneo.primeraJornada.atTime(15, 0).toString());
        carga.put("canchaId", torneo.canchaA);
        carga.put("usuarioId", torneo.usuarioId);
        long partidoId = torneo.create(torneo.fixture() + "/partidos", carga);

        mvc.perform(get(torneo.partido(partidoId) + "/planilla"))
                .andExpect(jsonPath("$.visitante.inscripcionEquipoId").doesNotExist())
                .andExpect(jsonPath("$.visitante.jugadores.length()").value(0));
        Map<String, Object> planilla = new LinkedHashMap<>();
        planilla.put("jugadores", List.of(EscenarioTorneo.jugador(nuestro.jugadores().get(0), true, true),
                EscenarioTorneo.jugador(nuestro.jugadores().get(1), true, false)));
        planilla.put("usuarioId", torneo.usuarioId);
        torneo.putJson(torneo.partido(partidoId) + "/planilla", planilla).andExpect(status().isOk());
        torneo.confirmarPlanilla(partidoId);
        torneo.evento(partidoId, "GOL", 12, nuestro.jugadores().get(1), null, "JUGADA", null)
                .andExpect(status().isCreated());

        torneo.cerrar(partidoId, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("se carga a mano")));
        torneo.cerrar(partidoId, Map.of("golesLocal", 0, "golesVisitante", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("menos goles")));
        torneo.cerrar(partidoId, Map.of("golesLocal", 1, "golesVisitante", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.golesLocal").value(1))
                .andExpect(jsonPath("$.golesVisitante").value(2));
    }

    /** En eliminación directa el empate se define por penales, que no pueden faltar. */
    @Test
    void enEliminacionElEmpatePidePenales() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.con(mvc, json, "ELIMINACION_DIRECTA", false, 2, 2,
                List.of("DIF_GOLES"));
        Map<String, Object> generar = new LinkedHashMap<>();
        generar.put("fechaPrimeraJornada", torneo.primeraJornada.toString());
        generar.put("canchaIds", List.of(torneo.canchaA));
        generar.put("usuarioId", torneo.usuarioId);
        JsonNode finalDelTorneo = json.readTree(torneo.postJson(torneo.fixture() + "/eliminacion", generar)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get(0);
        long partidoId = finalDelTorneo.get("id").asLong();
        torneo.planillaCompleta(partidoId, finalDelTorneo.get("clubLocalId").asLong(),
                finalDelTorneo.get("clubVisitanteId").asLong(), 2).andExpect(status().isOk());
        torneo.confirmarPlanilla(partidoId);

        torneo.cerrar(partidoId, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("faltan penales")));
        torneo.cerrar(partidoId, Map.of("penalesLocal", 4, "penalesVisitante", 4))
                .andExpect(status().isBadRequest());
        torneo.cerrar(partidoId, Map.of("penalesLocal", 4, "penalesVisitante", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalesLocal").value(4));
    }

    /**
     * Partido de liga con la planilla guardada: los primeros {@code titulares}
     * de cada equipo de titulares y el resto suplentes.
     */
    private Partido partidoConPlanilla(EscenarioTorneo torneo, int titulares, boolean confirmar)
            throws Exception {
        JsonNode partido = torneo.generarLiga().get(0);
        long id = partido.get("id").asLong();
        long local = partido.get("clubLocalId").asLong();
        long visitante = partido.get("clubVisitanteId").asLong();
        torneo.planillaCompleta(id, local, visitante, titulares).andExpect(status().isOk());
        if (confirmar) {
            torneo.confirmarPlanilla(id);
        }
        return new Partido(id, torneo.equipoDe(local).jugadores(), torneo.equipoDe(visitante).jugadores());
    }

    private long idDe(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** El partido y los jugadores de cada equipo, en orden de dorsal. */
    private record Partido(long id, List<Long> local, List<Long> visitante) {
        long l(int i) {
            return local.get(i);
        }

        long v(int i) {
            return visitante.get(i);
        }
    }
}
