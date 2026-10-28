package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.repos.PartidoRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 7 - Fixture de punta a punta: tareas S2-14 (liga por rotación), S2-15
 * (llaves), S2-16 (cancha y horario sin choques), S2-17 (ajuste y carga
 * manual) y S2-18 (publicar).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class FixtureApiTest {
    private static final String TORNEOS = "/api/v1/torneos";
    private static final String INSCRIPCIONES = "/api/v1/torneos/inscripciones";

    @Autowired MockMvc mvc;
    @Autowired PartidoRepository partidos;
    @Autowired ObjectMapper json;

    private long ciudadId;
    private long usuarioId;
    private long canchaA;
    private long canchaB;
    private LocalDate primeraJornada;

    @BeforeEach
    void fixtures() throws Exception {
        long paisId = create("/api/v1/paises", Map.of("nombrePais", "Bolivia"));
        ciudadId = create("/api/v1/ciudades", Map.of("nombreCiudad", "Santa Cruz", "paisId", paisId));
        usuarioId = create("/api/v1/usuarios", Map.of("name", "org",
                "nombres", "Mauricio", "apellidos", "Valdivia",
                "email", "fixture" + System.nanoTime() + "@example.com",
                "password", "secreto123"));
        long sedeId = create(TORNEOS + "/sedes", Map.of("nombre", "Sede Central " + System.nanoTime(),
                "direccion", "Av. Siempre Viva 123", "ciudadId", ciudadId));
        canchaA = cancha(sedeId, "Cancha A");
        canchaB = cancha(sedeId, "Cancha B");
        primeraJornada = LocalDate.now().plusDays(30);
    }

    /** Tarea S2-14: con 4 equipos salen 3 jornadas de 2 partidos cada una. */
    @Test
    void generaLaLigaPorRotacionConTodosContraTodos() throws Exception {
        Escenario liga = escenario("LIGA", 4, 4);

        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(6));

        mvc.perform(get(liga.fixture())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].jornada").value(1))
                .andExpect(jsonPath("$[0].fase").value("LIGA"))
                .andExpect(jsonPath("$[0].estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$[0].canchaNombre").value("Cancha A"))
                .andExpect(jsonPath("$[0].canchaSuperficie").value("CESPED_SINTETICO"))
                .andExpect(jsonPath("$[5].jornada").value(3));

        // Cada equipo juega 3 partidos y ninguno se repite como rival.
        String cuerpo = mvc.perform(get(liga.fixture())).andReturn().getResponse()
                .getContentAsString();
        var partidos = json.readTree(cuerpo);
        Map<String, Integer> porEquipo = new LinkedHashMap<>();
        for (var partido : partidos) {
            porEquipo.merge(partido.get("clubLocalId").asText(), 1, Integer::sum);
            porEquipo.merge(partido.get("clubVisitanteId").asText(), 1, Integer::sum);
        }
        assertThat(porEquipo).hasSize(4)
                .allSatisfy((club, jugados) ->
                        assertThat(jugados).isEqualTo(3));
    }

    /** Tarea S2-14: con 5 equipos cada fecha deja uno libre. */
    @Test
    void conUnNumeroImparDeEquiposUnoQuedaLibreCadaFecha() throws Exception {
        Escenario liga = escenario("LIGA", 5, 5);

        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(10));

        mvc.perform(get(liga.fixture()))
                .andExpect(jsonPath("$[9].jornada").value(5));
    }

    /** Tarea S2-16: dos partidos de la misma fecha no comparten cancha y horario. */
    @Test
    void nuncaPoneDosPartidosEnLaMismaCanchaYHorario() throws Exception {
        Escenario liga = escenario("LIGA", 4, 4);
        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated());

        var partidos = json.readTree(mvc.perform(get(liga.fixture())).andReturn()
                .getResponse().getContentAsString());
        Set<String> ocupados = new HashSet<>();
        for (var partido : partidos) {
            String slot = partido.get("canchaId").asText() + "@" + partido.get("fechaInicio").asText();
            assertThat(ocupados.add(slot))
                    .withFailMessage("La cancha y el horario %s están repetidos", slot).isTrue();
        }
    }

    /** Tarea S2-16: con una sola cancha no alcanza el día si los horarios no entran. */
    @Test
    void avisaCuandoNoHayCanchaNiHorarioLibre() throws Exception {
        Escenario liga = escenario("LIGA", 4, 4);
        Map<String, Object> body = generar(canchaA);
        body.put("duracionPartidoMin", 600);

        postJson(liga.fixture() + "/liga", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        containsString("No hay cancha ni horario libres")));
    }

    @Test
    void noGeneraDosVecesNiSinEquiposAprobados() throws Exception {
        Escenario sinAprobar = escenario("LIGA", 4, 0);
        postJson(sinAprobar.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        containsString("inscripción aprobada")));

        Escenario liga = escenario("LIGA", 4, 4);
        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated());
        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isConflict());

        // Borrar el fixture lo deja generable otra vez.
        mvc.perform(delete(liga.fixture()).param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isNoContent());
        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated());
    }

    /** Tarea S2-15: 4 equipos, semifinales, y la final se arma al cerrar la ronda. */
    @Test
    void generaLasLlavesYAvanzaDeRondaConLosGanadores() throws Exception {
        Escenario copa = escenario("ELIMINACION_DIRECTA", 4, 4);

        postJson(copa.fixture() + "/eliminacion", generar(canchaA, canchaB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fase").value("SEMIFINAL"));

        postJson(copa.fixture() + "/avanzar", generar(canchaA, canchaB))
                .andExpect(status().isConflict());

        var semis = json.readTree(mvc.perform(get(copa.fixture())).andReturn().getResponse()
                .getContentAsString());
        for (var semi : semis) {
            cerrarConGanadorLocal(semi.get("id").asLong());
        }

        postJson(copa.fixture() + "/avanzar", generar(canchaA, canchaB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fase").value("FINAL"));
    }

    /** Tarea S2-15: con 3 equipos uno pasa directo a la final. */
    @Test
    void conTresEquiposUnoPasaDirectoALaFinal() throws Exception {
        Escenario copa = escenario("ELIMINACION_DIRECTA", 3, 3);

        postJson(copa.fixture() + "/eliminacion", generar(canchaA, canchaB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fase").value("SEMIFINAL"));

        var semis = json.readTree(mvc.perform(get(copa.fixture())).andReturn().getResponse()
                .getContentAsString());
        cerrarConGanadorLocal(semis.get(0).get("id").asLong());

        postJson(copa.fixture() + "/avanzar", generar(canchaA, canchaB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fase").value("FINAL"));
    }

    /** Tarea S2-17: mover un partido de cancha y horario, con la misma validación. */
    @Test
    void ajustaUnPartidoAManoYRechazaElChoque() throws Exception {
        Escenario liga = escenario("LIGA", 4, 4);
        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated());
        var partidos = json.readTree(mvc.perform(get(liga.fixture())).andReturn().getResponse()
                .getContentAsString());
        long primero = partidos.get(0).get("id").asLong();
        String otroHorario = partidos.get(1).get("fechaInicio").asText();
        long canchaDelOtro = partidos.get(1).get("canchaId").asLong();

        Map<String, Object> choque = new LinkedHashMap<>();
        choque.put("fechaInicio", otroHorario);
        choque.put("canchaId", canchaDelOtro);
        choque.put("usuarioId", usuarioId);
        putJson(TORNEOS + "/partidos/" + primero, choque).andExpect(status().isConflict());

        Map<String, Object> libre = new LinkedHashMap<>();
        libre.put("fechaInicio", primeraJornada.plusDays(1).atTime(18, 0).toString());
        libre.put("canchaId", canchaA);
        libre.put("arbitro", "Juan Pérez");
        libre.put("usuarioId", usuarioId);
        putJson(TORNEOS + "/partidos/" + primero, libre).andExpect(status().isOk())
                .andExpect(jsonPath("$.arbitro").value("Juan Pérez"))
                .andExpect(jsonPath("$.canchaId").value(canchaA));
    }

    /** Tarea S2-17 y decisión 3.6: en un campeonato externo no se genera, se carga a mano. */
    @Test
    void enUnCampeonatoExternoNoSeGeneraFixtureYSeCarganLosPartidosAMano() throws Exception {
        Escenario externo = escenario("EXTERNO_LIGA", 2, 2);

        postJson(externo.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        containsString("campeonato externo")));

        Map<String, Object> partido = new LinkedHashMap<>();
        partido.put("clubLocalId", externo.clubes().get(0));
        partido.put("clubVisitanteId", externo.clubes().get(1));
        partido.put("fechaInicio", primeraJornada.atTime(15, 0).toString());
        partido.put("canchaId", canchaA);
        partido.put("usuarioId", usuarioId);
        postJson(externo.fixture() + "/partidos", partido).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fase").value("LIGA"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"));

        // El mismo horario y la misma cancha, otra vez, no entran.
        postJson(externo.fixture() + "/partidos", partido).andExpect(status().isConflict());
    }

    /** Tarea S2-18: publicar deja el torneo EN_CURSO y no se publica dos veces. */
    @Test
    void publicaElFixtureDelTorneo() throws Exception {
        Escenario liga = escenario("LIGA", 4, 4);
        String publicar = TORNEOS + "/" + liga.torneoId() + "/fixture/publicar";

        mvc.perform(post(publicar).param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        containsString("no tiene ningún partido")));

        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated());
        mvc.perform(post(publicar).param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.totalPartidos").value(6))
                .andExpect(jsonPath("$.totalCategorias").value(1));
        mvc.perform(post(publicar).param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isConflict());
    }

    /** Tarea S2-18: el fixture también se exporta en PDF. */
    @Test
    void exportaElFixtureEnPdf() throws Exception {
        Escenario liga = escenario("LIGA", 4, 4);

        mvc.perform(get(liga.fixture() + "/pdf")).andExpect(status().isBadRequest());

        postJson(liga.fixture() + "/liga", generar(canchaA, canchaB))
                .andExpect(status().isCreated());
        byte[] pdf = mvc.perform(get(liga.fixture() + "/pdf")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(800);
    }

    @Test
    void validaElTorneoYLaCategoriaDeLaRuta() throws Exception {
        Escenario liga = escenario("LIGA", 2, 2);

        mvc.perform(get(TORNEOS + "/" + Long.MAX_VALUE + "/categorias/1/fixture"))
                .andExpect(status().isNotFound());
        mvc.perform(get(TORNEOS + "/" + liga.torneoId() + "/categorias/" + Long.MAX_VALUE
                        + "/fixture"))
                .andExpect(status().isNotFound());
        postJson(liga.fixture() + "/liga", generar(Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    /**
     * Cierra un partido con 2-0 para el local. Va por el repositorio porque el
     * CRUD compartido no expone los goles: los escribe la planilla (HU 10), que
     * es del sprint 3.
     */
    private void cerrarConGanadorLocal(long partidoId) {
        Partido partido = partidos.findById(partidoId).orElseThrow();
        partido.setGolesLocal((short) 2);
        partido.setGolesVisitante((short) 0);
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidos.saveAndFlush(partido);
    }

    private Escenario escenario(String formato, int equipos, int aprobados) throws Exception {
        boolean externo = formato.startsWith("EXTERNO");
        long torneoId = torneo(externo ? "LIGA" : formato, externo);
        long categoriaId = categoria(torneoId, equipos + 1);
        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones"))
                .andExpect(status().isOk());
        List<Long> clubes = new ArrayList<>();
        for (int i = 0; i < equipos; i++) {
            long clubId = club("Club " + System.nanoTime());
            clubes.add(clubId);
            long inscripcionId = idDe(postJson(INSCRIPCIONES, Map.of(
                    "torneoCategoriaId", categoriaId, "clubId", clubId, "usuarioId", usuarioId))
                    .andExpect(status().isCreated()));
            if (i < aprobados) {
                Map<String, Object> cambio = new LinkedHashMap<>();
                cambio.put("estado", "APROBADA");
                cambio.put("usuarioId", usuarioId);
                putJson(INSCRIPCIONES + "/" + inscripcionId + "/estado", cambio)
                        .andExpect(status().isOk());
            }
        }
        return new Escenario(torneoId, categoriaId, clubes);
    }

    private Map<String, Object> generar(Long... canchaIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fechaPrimeraJornada", primeraJornada.toString());
        body.put("horaPrimerPartido", "09:00:00");
        body.put("diasEntreJornadas", 7);
        body.put("canchaIds", List.of(canchaIds));
        body.put("usuarioId", usuarioId);
        return body;
    }

    private long torneo(String formato, boolean externo) throws Exception {
        LocalDate hoy = LocalDate.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Copa Fixture");
        body.put("temporada", hoy.getYear());
        body.put("tipo", "OFICIAL");
        body.put("ambito", externo ? "EXTERNO" : "INTERNO");
        if (externo) {
            body.put("organizadorExterno", "Liga Cruceña");
        }
        body.put("formato", formato);
        body.put("fechaInicio", hoy.plusDays(20).toString());
        body.put("fechaFin", hoy.plusDays(200).toString());
        body.put("fechaLimiteInscripcion", hoy.plusDays(10).toString());
        body.put("usuarioId", usuarioId);
        return create(TORNEOS, body);
    }

    private long categoria(long torneoId, int cupo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Sub-14");
        body.put("edadMinima", 12);
        body.put("edadMaxima", 14);
        body.put("cupoMaximo", cupo);
        body.put("duracionTiempoMin", 35);
        body.put("jugadoresEnCancha", 11);
        return create(TORNEOS + "/" + torneoId + "/categorias", body);
    }

    private long cancha(long sedeId, String nombre) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sedeId", sedeId);
        body.put("nombre", nombre);
        body.put("tipoSuperficie", "CESPED_SINTETICO");
        body.put("descripcion", "100x60, iluminada");
        return create(TORNEOS + "/canchas", body);
    }

    private long club(String nombre) throws Exception {
        return create("/api/v1/clubes", Map.of("nombreClub", nombre, "ciudadId", ciudadId));
    }

    private long create(String url, Map<String, Object> body) throws Exception {
        return idDe(postJson(url, body).andExpect(status().isCreated()));
    }

    private long idDe(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions putJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private record Escenario(long torneoId, long categoriaId, List<Long> clubes) {
        String fixture() {
            return TORNEOS + "/" + torneoId + "/categorias/" + categoriaId + "/fixture";
        }
    }
}
