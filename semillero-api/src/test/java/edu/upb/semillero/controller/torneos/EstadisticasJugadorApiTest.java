package edu.upb.semillero.controller.torneos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa datos deportivos reales de prueba, sin crear análisis ni mediciones de video. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "CONSULTA")
@Transactional
class EstadisticasJugadorApiTest {
    private static final String STATS = "/api/v1/torneos/jugadores/72001/estadisticas";
    private static final String COMPARE = "/api/v1/torneos/jugadores/comparacion";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/comparador-fixture.sql"));
            return null;
        });
    }

    @Test
    void comparaDosJugadoresConResultadosDiferentesSinAnalisisDeVideo() throws Exception {
        mvc.perform(get(COMPARE).param("jugadorAId", "72001").param("jugadorBId", "72002")
                        .param("temporada", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jugadorA.jugadorId").value(72001))
                .andExpect(jsonPath("$.jugadorA.partidosJugados").value(1))
                .andExpect(jsonPath("$.jugadorA.goles").value(2))
                .andExpect(jsonPath("$.jugadorA.asistencias").value(1))
                .andExpect(jsonPath("$.jugadorA.minutosJugados").value(80))
                .andExpect(jsonPath("$.jugadorA.minutosRegistrados").value(80))
                .andExpect(jsonPath("$.jugadorA.minutosCompletos").value(true))
                .andExpect(jsonPath("$.jugadorA.dorsal").value(9))
                .andExpect(jsonPath("$.jugadorA.dorsalPartidoId").value(73001))
                .andExpect(jsonPath("$.jugadorA.dorsalClubId").value(71001))
                .andExpect(jsonPath("$.jugadorA.calificacionMedia").value(nullValue()))
                .andExpect(jsonPath("$.jugadorB.jugadorId").value(72002))
                .andExpect(jsonPath("$.jugadorB.goles").value(1))
                .andExpect(jsonPath("$.jugadorB.asistencias").value(1))
                .andExpect(jsonPath("$.jugadorB.minutosJugados").value(90))
                .andExpect(jsonPath("$.jugadorB.dorsal").value(11));
        assertThat(jdbc.queryForObject("select count(*) from player_match_metrics", Integer.class)).isZero();
    }

    @Test
    void devuelveElResumenIndividualSinDatosPersonales() throws Exception {
        mvc.perform(get(STATS).param("temporada", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.goles").value(2))
                .andExpect(jsonPath("$.temporada").value(2026))
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void unSuplenteQueNoEntroNoCuentaComoPartidoJugado() throws Exception {
        mvc.perform(get("/api/v1/torneos/jugadores/72004/estadisticas"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partidosJugados").value(0))
                .andExpect(jsonPath("$.minutosJugados").value(0))
                .andExpect(jsonPath("$.goles").value(0)).andExpect(jsonPath("$.asistencias").value(0))
                .andExpect(jsonPath("$.dorsal").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAMADO", "EN_CURSO", "CANCELADO"})
    void noIncluyePartidosQueNoEstanFinalizados(String state) throws Exception {
        match(73002, "2026-11-01 10:00:00", state);
        participation(74005, 73002, 72001, 71001, 60, 99);
        goal(75004, 73002, 74005, null, 71001, "JUGADA");
        mvc.perform(get(STATS).param("temporada", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.goles").value(2)).andExpect(jsonPath("$.minutosJugados").value(80))
                .andExpect(jsonPath("$.dorsal").value(9));
    }

    @Test
    void noCuentaParticipacionesDeUnWalkover() throws Exception {
        match(73002, "2026-11-01 10:00:00", "FINALIZADO");
        jdbc.update("update partidos set ganador_walkover_id=71001 where id=73002");
        participation(74005, 73002, 72001, 71001, 60, 99);
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.minutosJugados").value(80));
    }

    @Test
    void utilizaLaTemporadaDelTorneoInclusoSiSeJuegaEnOtroAnio() throws Exception {
        tournament(76001, 77001, 2025);
        match(73002, "2026-11-01 10:00:00", "FINALIZADO");
        jdbc.update("update partidos set torneo_categorias_id=77001,fase='LIGA' where id=73002");
        participation(74005, 73002, 72001, 71001, 40, 20);
        mvc.perform(get(STATS).param("temporada", "2025"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.minutosJugados").value(40)).andExpect(jsonPath("$.dorsal").value(20));
        mvc.perform(get(STATS).param("temporada", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.minutosJugados").value(80));
    }

    @Test
    void filtraPorTorneoSinMezclarLasOtrasParticipaciones() throws Exception {
        tournament(76001, 77001, 2026);
        jdbc.update("update partidos set torneo_categorias_id=77001,fase='LIGA' where id=73001");
        match(73002, "2026-11-01 10:00:00", "FINALIZADO");
        participation(74005, 73002, 72001, 71001, 40, 20);
        mvc.perform(get(STATS).param("torneoId", "76001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.torneoId").value(76001))
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.minutosJugados").value(80));
    }

    @Test
    void conservaElDorsalMasRecienteConDesempatePorPartido() throws Exception {
        match(73002, "2026-10-20 10:00:00", "FINALIZADO");
        participation(74005, 73002, 72001, 71001, 40, 17);
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dorsal").value(17))
                .andExpect(jsonPath("$.dorsalPartidoId").value(73002))
                .andExpect(jsonPath("$.minutosJugados").value(120));
    }

    @Test
    void unDorsalCeroSePresentaComoNoDisponible() throws Exception {
        jdbc.update("update participaciones_partido set dorsal=0 where id=74001");
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dorsal").value(nullValue()))
                .andExpect(jsonPath("$.dorsalPartidoId").value(nullValue()));
    }

    @Test
    void priorizaLosMinutosRegistradosSobreElIntervaloDeEntradaYSalida() throws Exception {
        jdbc.update("update participaciones_partido set minutos_jugados=70 where id=74001");
        mvc.perform(get(STATS)).andExpect(status().isOk()).andExpect(jsonPath("$.minutosJugados").value(70));
    }

    @Test
    void usaElIntervaloDeEntradaYSalidaParaLosRegistrosAntiguos() throws Exception {
        jdbc.update("update participaciones_partido set minutos_jugados=null,minuto_entrada=10 where id=74001");
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.minutosJugados").value(70))
                .andExpect(jsonPath("$.minutosCompletos").value(true));
    }

    @Test
    void distingueUnTotalIncompletoDeUnCeroReal() throws Exception {
        jdbc.update("update participaciones_partido set minutos_jugados=null,minuto_salida=null where id=74001");
        match(73002, "2026-11-01 10:00:00", "FINALIZADO");
        participation(74005, 73002, 72001, 71001, 25, 17);
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(2))
                .andExpect(jsonPath("$.minutosJugados").value(nullValue()))
                .andExpect(jsonPath("$.minutosCompletos").value(false))
                .andExpect(jsonPath("$.minutosRegistrados").value(25));
    }

    @Test
    void unGolAcreditaParticipacionAunqueFaltenSusMinutos() throws Exception {
        jdbc.update("""
                update participaciones_partido set es_titular=false,minutos_jugados=null,minuto_salida=null
                where id=74001
                """);
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.goles").value(2))
                .andExpect(jsonPath("$.minutosJugados").value(nullValue()));
    }

    @Test
    void noPierdeLosGolesCuandoLaPlanillaTieneCeroMinutos() throws Exception {
        jdbc.update("""
                update participaciones_partido set es_titular=false,minutos_jugados=0,minuto_salida=0
                where id=74001
                """);
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.goles").value(2))
                .andExpect(jsonPath("$.asistencias").value(1))
                .andExpect(jsonPath("$.minutosJugados").value(0));
    }

    @Test
    void unaTitularidadNoSePierdePorLaFaltaDeCalculoDeMinutos() throws Exception {
        jdbc.update("delete from eventos_partido where participaciones_partido_id=74001 or participacion_rel_id=74001");
        jdbc.update("update participaciones_partido set minutos_jugados=0,minuto_salida=0 where id=74001");
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.goles").value(0));
    }

    @Test
    void noSumaAutogolesNiLesAsignaAsistencias() throws Exception {
        goal(75004, 73001, 74001, 74002L, 71002, "AUTOGOL");
        goal(75005, 73001, 74001, 74002L, 71001, "AUTOGOL");
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.goles").value(2)).andExpect(jsonPath("$.asistencias").value(1));
        mvc.perform(get("/api/v1/torneos/jugadores/72002/estadisticas"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asistencias").value(1));
    }

    @Test
    void unAutogolAcreditaParticipacionSinConvertirseEnGolAFavor() throws Exception {
        jdbc.update("delete from eventos_partido where participaciones_partido_id=74001 or participacion_rel_id=74001");
        jdbc.update("""
                update participaciones_partido set es_titular=false,minutos_jugados=null,minuto_salida=null
                where id=74001
                """);
        goal(75004, 73001, 74001, null, 71002, "AUTOGOL");
        mvc.perform(get(STATS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosJugados").value(1))
                .andExpect(jsonPath("$.goles").value(0))
                .andExpect(jsonPath("$.minutosJugados").value(nullValue()));
    }

    @Test
    void noAsignaAsistenciasEntreClubesNiEntrePartidosDiferentes() throws Exception {
        goal(75004, 73001, 74003, 74001L, 71002, "JUGADA");
        match(73002, "2026-11-01 10:00:00", "FINALIZADO");
        participation(74005, 73002, 72003, 71002, 90, 4);
        goal(75005, 73002, 74005, 74001L, 71002, "JUGADA");
        mvc.perform(get(STATS)).andExpect(status().isOk()).andExpect(jsonPath("$.asistencias").value(1));
    }

    @Test
    void permiteCompararUnJugadorConsigoMismoSinDuplicarTotales() throws Exception {
        mvc.perform(get(COMPARE).param("jugadorAId", "72001").param("jugadorBId", "72001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jugadorA.goles").value(2))
                .andExpect(jsonPath("$.jugadorB.goles").value(2));
    }

    @Test
    void filtraParticipacionesYDevuelveLosMinutosPersistidos() throws Exception {
        mvc.perform(get("/api/v1/participaciones-partido").param("jugadorId", "72001")
                        .param("partidoId", "73001").param("clubId", "71001").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].jugadorId").value(72001))
                .andExpect(jsonPath("$.content[0].minutosJugados").value(80))
                .andExpect(jsonPath("$.content[0].dorsal").value(9));
        mvc.perform(get("/api/v1/participaciones-partido").param("jugadorId", "72001").param("clubId", "71002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "32768", "invalida"})
    void rechazaTemporadasInvalidas(String season) throws Exception {
        mvc.perform(get(STATS).param("temporada", season)).andExpect(status().isBadRequest());
    }

    @Test
    void rechazaIdsInexistentesYParametrosDeComparacionInvalidos() throws Exception {
        mvc.perform(get("/api/v1/torneos/jugadores/999999/estadisticas")).andExpect(status().isNotFound());
        mvc.perform(get(STATS).param("torneoId", "999999")).andExpect(status().isNotFound());
        mvc.perform(get(COMPARE).param("jugadorAId", "72001")).andExpect(status().isBadRequest());
        mvc.perform(get(COMPARE).param("jugadorAId", "-1").param("jugadorBId", "72002"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/participaciones-partido").param("jugadorId", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void lasEstadisticasYLaComparacionRequierenSesion() throws Exception {
        mvc.perform(get(STATS)).andExpect(status().isUnauthorized());
        mvc.perform(get(COMPARE).param("jugadorAId", "72001").param("jugadorBId", "72002"))
                .andExpect(status().isUnauthorized());
    }

    private void match(long id, String start, String state) {
        jdbc.update("""
                insert into partidos(id,fecha_inicio,club_local_id,club_visitante_id,estado)
                values (?,cast(? as timestamp),71001,71002,?)
                """, id, start, state);
    }

    private void participation(long id, long matchId, long playerId, long clubId, int minutes, int jersey) {
        jdbc.update("""
                insert into participaciones_partido(id,partidos_id,jugadores_id,clubes_id,es_titular,
                dorsal,minuto_entrada,minuto_salida,minutos_jugados) values (?,?,?,?,true,?,0,?,?)
                """, id, matchId, playerId, clubId, jersey, minutes, minutes);
    }

    private void goal(long id, long matchId, long authorId, Long assistantId, long clubId, String type) {
        jdbc.update("""
                insert into eventos_partido(id,partidos_id,tipo,minuto,participaciones_partido_id,
                participacion_rel_id,clubes_id,tipo_gol,users_id)
                values (?,?,'GOL',40,?,?,?,?,70001)
                """, id, matchId, authorId, assistantId, clubId, type);
    }

    private void tournament(long id, long categoryId, int season) {
        jdbc.update("""
                insert into torneos(id,nombre,temporada,tipo,formato,fecha_inicio,fecha_fin,
                fecha_limite_inscripcion,users_id) values (?,'Torneo stats',?,'OFICIAL','LIGA',
                '2026-01-01','2026-12-31','2025-12-31',70001)
                """, id, season);
        jdbc.update("""
                insert into torneo_categorias(id,torneos_id,nombre,edad_maxima,cupo_maximo,
                duracion_tiempo_min,jugadores_en_cancha) values (?,?,'Sub-16',16,8,45,11)
                """, categoryId, id);
    }
}
